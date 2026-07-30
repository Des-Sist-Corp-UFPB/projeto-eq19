package br.com.tabula.controller;

import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.SessionRepository;
import br.com.tabula.repository.SessionRepository.SessionData;
import br.com.tabula.service.AuditLogService;
import br.com.tabula.service.AuthenticatedUserService;
import br.com.tabula.service.SessionService;
import br.com.tabula.service.SessionService.RequestMetadata;
import br.com.tabula.service.SessionService.SessionException;
import br.com.tabula.service.SessionService.SessionInput;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SessionController {
    private SessionController() {}

    public static void register(Javalin app, HikariDataSource dataSource) {
        AuthenticatedUserService auth = new AuthenticatedUserService(dataSource);
        SessionService service = new SessionService(dataSource, new SessionRepository(dataSource),
                new AuditLogService(dataSource));

        app.get("/sessions", ctx -> authenticated(ctx, auth, service, principal ->
                ctx.json(service.list().stream().map(SessionResponse::from).toList())));
        app.get("/sessions/{id}", ctx -> authenticated(ctx, auth, service, principal ->
                ctx.json(SessionResponse.from(service.get(ctx.pathParam("id"))))));
        app.post("/sessions", ctx -> authenticated(ctx, auth, service, principal -> {
            SessionRequest request;
            try { request = ctx.bodyAsClass(SessionRequest.class); }
            catch (Exception ex) { throw SessionException.invalid("invalid_payload"); }
            SessionData created = service.create(principal, request.toInput(), metadata(ctx));
            ctx.status(201).json(SessionResponse.from(created));
        }));
        app.delete("/sessions/{id}", ctx -> authenticated(ctx, auth, service, principal -> {
            service.delete(principal, ctx.pathParam("id"), metadata(ctx));
            ctx.status(204);
        }));
    }

    private static void authenticated(Context ctx, AuthenticatedUserService auth, SessionService service,
                                      Handler handler) throws Exception {
        Optional<AuthenticatedPrincipal> principal = auth.resolve(ctx.header("Authorization"));
        if (principal.isEmpty()) {
            ctx.status(401).json(Map.of("error", "Sessão inválida ou expirada."));
            return;
        }
        try {
            handler.handle(principal.get());
        } catch (SessionException ex) {
            int status = switch (ex.kind()) {
                case FORBIDDEN -> 403;
                case NOT_FOUND -> 404;
                case CONFLICT -> 409;
                case INVALID -> 422;
            };
            service.auditRejected(principal.get(), ctx.pathParamMap().get("id"), ex.reason(), metadata(ctx));
            ctx.status(status).json(Map.of("error", switch (ex.kind()) {
                case FORBIDDEN -> "Operação não permitida.";
                case NOT_FOUND -> "Partida ou recurso relacionado não encontrado.";
                case CONFLICT -> "A operação conflita com o estado atual da partida.";
                case INVALID -> "Dados da partida inválidos.";
            }));
        }
    }

    private static RequestMetadata metadata(Context ctx) {
        return new RequestMetadata(ctx.ip(), ctx.userAgent());
    }

    private record SessionRequest(String gameId, String date, String location,
                                  List<String> participantIds, String winnerId,
                                  int duration, String notes) {
        SessionInput toInput() {
            return new SessionInput(gameId, date, location, participantIds, winnerId, duration, notes);
        }
    }

    private record SessionResponse(String id, String gameId, String date, String location,
                                   String organizerId, List<String> participantIds,
                                   String winnerId, int duration, String notes,
                                   List<String> photos, List<CommentResponse> comments) {
        static SessionResponse from(SessionData session) {
            return new SessionResponse(session.externalId(), session.gameId(),
                    session.dateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    session.location(), session.organizerId(), session.participantIds(),
                    session.winnerId(), session.duration(),
                    session.notes() == null ? "" : session.notes(), session.photos(),
                    session.comments().stream().map(CommentResponse::from).toList());
        }
    }

    private record CommentResponse(String id, String userId, String userName,
                                   String userAvatar, String content, String createdAt) {
        static CommentResponse from(SessionRepository.CommentData comment) {
            return new CommentResponse(comment.id(),
                    comment.userId() == null ? "system" : comment.userId(),
                    comment.userName() == null ? "Sistema" : comment.userName(),
                    "🎲", comment.content(),
                    comment.createdAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(AuthenticatedPrincipal principal) throws Exception;
    }
}

package br.com.tabula.controller;

import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.CommentRepository;
import br.com.tabula.repository.CommentRepository.CommentData;
import br.com.tabula.service.AuditLogService;
import br.com.tabula.service.AuthenticatedUserService;
import br.com.tabula.service.CommentService;
import br.com.tabula.service.CommentService.CommentException;
import br.com.tabula.service.CommentService.RequestMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

public final class CommentController {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CommentController() {}

    public static void register(Javalin app, HikariDataSource dataSource) {
        AuthenticatedUserService auth = new AuthenticatedUserService(dataSource);
        CommentService service = new CommentService(dataSource, new CommentRepository(),
                new AuditLogService(dataSource));

        app.get("/sessions/{sessionId}/comments", ctx -> authenticated(ctx, auth, service, principal ->
                ctx.json(service.list(ctx.pathParam("sessionId")).stream().map(CommentResponse::from).toList())));
        app.post("/sessions/{sessionId}/comments", ctx -> authenticated(ctx, auth, service, principal -> {
            CommentRequest request = parseRequest(ctx);
            CommentData created = service.create(principal, ctx.pathParam("sessionId"),
                    request.content(), metadata(ctx));
            ctx.status(201).json(CommentResponse.from(created));
        }));
        app.delete("/sessions/{sessionId}/comments/{commentId}", ctx ->
                authenticated(ctx, auth, service, principal -> {
                    service.delete(principal, ctx.pathParam("sessionId"), ctx.pathParam("commentId"), metadata(ctx));
                    ctx.status(204);
                }));
    }

    private static void authenticated(Context ctx, AuthenticatedUserService auth, CommentService service,
                                      Handler handler) throws Exception {
        Optional<AuthenticatedPrincipal> principal = auth.resolve(ctx.header("Authorization"));
        if (principal.isEmpty()) {
            ctx.status(401).json(Map.of("error", "Sessão inválida ou expirada."));
            return;
        }
        try { handler.handle(principal.get()); }
        catch (CommentException ex) {
            int status = switch (ex.kind()) {
                case FORBIDDEN -> 403;
                case NOT_FOUND -> 404;
                case INVALID -> 422;
            };
            service.auditRejected(principal.get(), ctx.pathParam("sessionId"),
                    ctx.pathParamMap().get("commentId"), ex.reason(), metadata(ctx));
            ctx.status(status).json(Map.of("error", switch (ex.kind()) {
                case FORBIDDEN -> "Operação não permitida.";
                case NOT_FOUND -> "Partida ou comentário não encontrado.";
                case INVALID -> "Comentário inválido.";
            }));
        }
    }

    private static CommentRequest parseRequest(Context ctx) throws CommentException {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(ctx.body());
            if (root == null || !root.isObject()) {
                throw CommentException.invalid("invalid_payload");
            }
            JsonNode contentNode = root.get("content");
            if (contentNode == null || !contentNode.isTextual()) {
                throw CommentException.invalid("invalid_payload");
            }
            return new CommentRequest(contentNode.asText());
        } catch (Exception ex) {
            throw CommentException.invalid("invalid_payload");
        }
    }

    private static RequestMetadata metadata(Context ctx) { return new RequestMetadata(ctx.ip(), ctx.userAgent()); }
    private record CommentRequest(String content) {}
    private record CommentResponse(String id, String userId, String userName, String userAvatar,
                                   String content, String createdAt) {
        static CommentResponse from(CommentData value) {
            return new CommentResponse(value.externalId(), value.userId() == null ? "system" : value.userId(),
                    value.userName() == null ? "Sistema" : value.userName(), value.userAvatar(), value.content(),
                    value.createdAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
    }
    @FunctionalInterface private interface Handler { void handle(AuthenticatedPrincipal principal) throws Exception; }
}

package br.com.tabula.controller;

import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.EventRepository;
import br.com.tabula.repository.EventRepository.EventData;
import br.com.tabula.service.AuditLogService;
import br.com.tabula.service.AuthenticatedUserService;
import br.com.tabula.service.EventService;
import br.com.tabula.service.EventService.CompletionInput;
import br.com.tabula.service.EventService.EventException;
import br.com.tabula.service.EventService.EventInput;
import br.com.tabula.service.EventService.RequestMetadata;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EventController {
    private EventController() {}

    public static void register(Javalin app, HikariDataSource dataSource) {
        AuthenticatedUserService auth = new AuthenticatedUserService(dataSource);
        EventRepository repository = new EventRepository(dataSource);
        EventService service = new EventService(dataSource, repository, new AuditLogService(dataSource));

        app.get("/events", ctx -> withPrincipal(ctx, auth, service, principal -> ctx.json(
                service.list().stream().map(EventResponse::from).toList())));
        app.get("/events/{id}", ctx -> withPrincipal(ctx, auth, service, principal ->
                ctx.json(EventResponse.from(service.get(ctx.pathParam("id"))))));
        app.post("/events", ctx -> withPrincipal(ctx, auth, service, principal -> {
            EventRequest request = body(ctx, EventRequest.class);
            EventData event = service.create(principal, request.toInput(), metadata(ctx));
            ctx.status(201).json(EventResponse.from(event));
        }));
        app.patch("/events/{id}", ctx -> withPrincipal(ctx, auth, service, principal -> {
            EventRequest request = body(ctx, EventRequest.class);
            ctx.json(EventResponse.from(service.update(
                    principal, ctx.pathParam("id"), request.toInput(), metadata(ctx))));
        }));
        app.post("/events/{id}/join", ctx -> withPrincipal(ctx, auth, service, principal -> {
            var result = service.join(principal, ctx.pathParam("id"), metadata(ctx));
            ctx.json(Map.of("event", EventResponse.from(result.event()), "waitlisted", result.waitlisted()));
        }));
        app.post("/events/{id}/leave", ctx -> withPrincipal(ctx, auth, service, principal -> {
            var result = service.leave(principal, ctx.pathParam("id"), metadata(ctx));
            ctx.json(Map.of("event", EventResponse.from(result.event()), "promoted", result.promoted()));
        }));
        app.post("/events/{id}/cancel", ctx -> withPrincipal(ctx, auth, service, principal ->
                ctx.json(EventResponse.from(service.cancel(
                        principal, ctx.pathParam("id"), metadata(ctx))))));
        app.post("/events/{id}/complete", ctx -> withPrincipal(ctx, auth, service, principal -> {
            CompleteRequest request = body(ctx, CompleteRequest.class);
            ctx.json(EventResponse.from(service.complete(
                    principal, ctx.pathParam("id"), request.toInput(), metadata(ctx))));
        }));
    }

    private static void withPrincipal(Context ctx, AuthenticatedUserService auth, EventService service,
                                      EventHandler handler)
            throws Exception {
        Optional<AuthenticatedPrincipal> principal = auth.resolve(ctx.header("Authorization"));
        if (principal.isEmpty()) {
            ctx.status(401).json(Map.of("error", "Sessão inválida ou expirada."));
            return;
        }
        try {
            handler.handle(principal.get());
        } catch (InvalidBodyException ex) {
            ctx.status(422).json(Map.of("error", "Payload inválido."));
        } catch (EventException ex) {
            int status = switch (ex.kind()) {
                case FORBIDDEN -> 403;
                case NOT_FOUND -> 404;
                case CONFLICT -> 409;
                case INVALID -> 422;
            };
            service.auditRejected(principal.get(), ctx.pathParamMap().get("id"), ex.reason(), metadata(ctx));
            ctx.status(status).json(Map.of("error", publicMessage(ex.kind())));
        }
    }

    private static String publicMessage(EventException.Kind kind) {
        return switch (kind) {
            case FORBIDDEN -> "Operação não permitida.";
            case NOT_FOUND -> "Evento não encontrado.";
            case CONFLICT -> "A operação conflita com o estado atual do evento.";
            case INVALID -> "Dados ou transição inválidos.";
        };
    }

    private static <T> T body(Context ctx, Class<T> type) throws InvalidBodyException {
        try {
            return ctx.bodyAsClass(type);
        } catch (Exception ex) {
            throw new InvalidBodyException();
        }
    }

    private static RequestMetadata metadata(Context ctx) {
        return new RequestMetadata(ctx.ip(), ctx.userAgent());
    }

    private record EventRequest(String gameId, String date, String time, String location,
                                int maxParticipants, String description) {
        EventInput toInput() {
            return new EventInput(gameId, date, time, location, maxParticipants, description);
        }
    }

    private record CompleteRequest(String winnerId, int duration, String notes,
                                   String initialComment, String photoUrl) {
        CompletionInput toInput() {
            return new CompletionInput(winnerId, duration, notes, initialComment, photoUrl);
        }
    }

    private record EventResponse(
            String id, String gameId, String date, String time, String location,
            int maxParticipants, List<String> participantIds, List<String> waitingListIds,
            String description, String organizerId, String status
    ) {
        private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
        private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

        static EventResponse from(EventData event) {
            return new EventResponse(event.externalId(), event.gameExternalId(),
                    event.dateTime().toLocalDate().format(DATE), event.dateTime().toLocalTime().format(TIME),
                    event.location(), event.maxParticipants(), event.participantIds(), event.waitingListIds(),
                    event.description() == null ? "" : event.description(), event.organizerExternalId(),
                    event.status());
        }
    }

    @FunctionalInterface
    private interface EventHandler {
        void handle(AuthenticatedPrincipal principal) throws Exception;
    }

    private static final class InvalidBodyException extends Exception {}
}

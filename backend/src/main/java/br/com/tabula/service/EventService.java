package br.com.tabula.service;

import br.com.tabula.model.AuditAction;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.EventRepository;
import br.com.tabula.repository.EventRepository.EventData;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EventService {
    private final HikariDataSource dataSource;
    private final EventRepository repository;
    private final AuditLogService audit;

    public EventService(HikariDataSource dataSource, EventRepository repository, AuditLogService audit) {
        this.dataSource = dataSource;
        this.repository = repository;
        this.audit = audit;
    }

    public List<EventData> list() throws SQLException {
        return repository.findAll();
    }

    public EventData get(String id) throws EventException, SQLException {
        return repository.findByExternalId(id).orElseThrow(() -> EventException.notFound("event_not_found"));
    }

    public EventData create(AuthenticatedPrincipal actor, EventInput input, RequestMetadata metadata)
            throws EventException, SQLException {
        validate(input);
        return transaction(connection -> {
            long gameId = gameId(connection, input.gameId());
            EventData created = repository.insert(connection, "e_" + UUID.randomUUID(), gameId,
                    dateTime(input.date(), input.time()), input.location().trim(), clean(input.description()),
                    input.maxParticipants(), actor);
            record(connection, actor, AuditAction.EVENT_CREATED, created.externalId(), true, null, metadata);
            return created;
        });
    }

    public EventData update(AuthenticatedPrincipal actor, String id, EventInput input, RequestMetadata metadata)
            throws EventException, SQLException {
        validate(input);
        return transaction(connection -> {
            EventData event = locked(connection, id);
            requireOrganizer(actor, event);
            requireActive(event);
            if (repository.participantCount(connection, event.databaseId()) > input.maxParticipants()) {
                throw EventException.conflict("capacity_below_current_participants");
            }
            repository.update(connection, event.databaseId(), gameId(connection, input.gameId()),
                    dateTime(input.date(), input.time()), input.location().trim(), clean(input.description()),
                    input.maxParticipants());
            record(connection, actor, AuditAction.EVENT_UPDATED, id, true, null, metadata);
            return repository.findByExternalId(connection, id, false).orElseThrow();
        });
    }

    public JoinResult join(AuthenticatedPrincipal actor, String id, RequestMetadata metadata)
            throws EventException, SQLException {
        return transaction(connection -> {
            EventData event = locked(connection, id);
            requireActive(event);
            if (repository.hasMembership(connection, event.databaseId(), actor.getDatabaseId())) {
                throw EventException.conflict("already_joined");
            }
            boolean waitlisted = repository.participantCount(connection, event.databaseId())
                    >= event.maxParticipants();
            repository.insertMembership(connection, event.databaseId(), actor.getDatabaseId(),
                    waitlisted ? "WAITING" : "PARTICIPANT",
                    waitlisted ? repository.nextQueueOrder(connection, event.databaseId()) : null);
            record(connection, actor, waitlisted ? AuditAction.EVENT_WAITLISTED : AuditAction.EVENT_JOINED,
                    id, true, null, metadata);
            return new JoinResult(
                    repository.findByExternalId(connection, id, false).orElseThrow(), waitlisted);
        });
    }

    public LeaveResult leave(AuthenticatedPrincipal actor, String id, RequestMetadata metadata)
            throws EventException, SQLException {
        return transaction(connection -> {
            EventData event = locked(connection, id);
            requireActive(event);
            if (event.organizerDatabaseId() == actor.getDatabaseId()) {
                throw EventException.invalid("organizer_cannot_leave");
            }
            var membership = repository.membership(connection, event.databaseId(), actor.getDatabaseId())
                    .orElseThrow(() -> EventException.conflict("not_joined"));
            repository.deleteMembership(connection, event.databaseId(), actor.getDatabaseId());
            Long promoted = null;
            if ("PARTICIPANT".equals(membership.type())) {
                promoted = repository.promoteFirstWaiting(connection, event.databaseId()).orElse(null);
            }
            record(connection, actor, AuditAction.EVENT_LEFT, id, true, null, metadata);
            if (promoted != null) {
                record(connection, actor, AuditAction.EVENT_WAITLIST_PROMOTED, id, true, null, metadata);
            }
            return new LeaveResult(repository.findByExternalId(connection, id, false).orElseThrow(),
                    promoted != null);
        });
    }

    public EventData cancel(AuthenticatedPrincipal actor, String id, RequestMetadata metadata)
            throws EventException, SQLException {
        return transaction(connection -> {
            EventData event = locked(connection, id);
            requireOrganizer(actor, event);
            requireActive(event);
            repository.updateStatus(connection, event.databaseId(), "cancelled");
            record(connection, actor, AuditAction.EVENT_CANCELLED, id, true, null, metadata);
            return repository.findByExternalId(connection, id, false).orElseThrow();
        });
    }

    public EventData complete(AuthenticatedPrincipal actor, String id, CompletionInput input,
                              RequestMetadata metadata) throws EventException, SQLException {
        if (input == null || input.duration() <= 0 || input.duration() > 1440) {
            throw EventException.invalid("invalid_duration");
        }
        return transaction(connection -> {
            EventData event = locked(connection, id);
            requireOrganizer(actor, event);
            requireActive(event);
            Long winnerId = repository.findUserDatabaseId(connection, clean(input.winnerId()));
            if (input.winnerId() != null && !input.winnerId().isBlank()
                    && (winnerId == null || !event.participantIds().contains(input.winnerId()))) {
                throw EventException.invalid("invalid_winner");
            }
            repository.createSession(connection, event, "s_" + UUID.randomUUID(), winnerId,
                    input.duration(), clean(input.notes()), clean(input.initialComment()), clean(input.photoUrl()));
            repository.updateStatus(connection, event.databaseId(), "completed");
            record(connection, actor, AuditAction.EVENT_COMPLETED, id, true, null, metadata);
            return repository.findByExternalId(connection, id, false).orElseThrow();
        });
    }

    public void auditRejected(AuthenticatedPrincipal actor, String id, String reason, RequestMetadata metadata) {
        audit.recordBestEffort(actor, AuditAction.EVENT_OPERATION_REJECTED, "EVENT", id, false,
                metadata.ipAddress(), metadata.userAgent(), Map.of("reason", reason));
    }

    private long gameId(Connection connection, String externalId) throws EventException, SQLException {
        try {
            return repository.requireGameId(connection, externalId);
        } catch (SQLException ex) {
            if ("game_not_found".equals(ex.getMessage())) throw EventException.invalid("game_not_found");
            throw ex;
        }
    }

    private EventData locked(Connection connection, String id) throws SQLException, EventException {
        return repository.findByExternalId(connection, id, true)
                .orElseThrow(() -> EventException.notFound("event_not_found"));
    }

    private static void requireOrganizer(AuthenticatedPrincipal actor, EventData event) throws EventException {
        if (event.organizerDatabaseId() != actor.getDatabaseId()) throw EventException.forbidden("not_organizer");
    }

    private static void requireActive(EventData event) throws EventException {
        if (!"active".equals(event.status())) throw EventException.conflict("event_not_active");
    }

    private static void validate(EventInput input) throws EventException {
        if (input == null || blank(input.gameId()) || blank(input.date()) || blank(input.time())
                || blank(input.location()) || input.maxParticipants() < 1 || input.maxParticipants() > 100
                || input.location().trim().length() > 512
                || (input.description() != null && input.description().length() > 4000)) {
            throw EventException.invalid("invalid_event");
        }
        dateTime(input.date(), input.time());
    }

    private static LocalDateTime dateTime(String date, String time) throws EventException {
        try {
            return LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time));
        } catch (DateTimeException ex) {
            throw EventException.invalid("invalid_date_time");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void record(Connection connection, AuthenticatedPrincipal actor, AuditAction action,
                        String id, boolean success, String reason, RequestMetadata metadata) throws SQLException {
        audit.record(connection, actor, action, "EVENT", id, success,
                metadata.ipAddress(), metadata.userAgent(),
                reason == null ? Map.of() : Map.of("reason", reason));
    }

    private <T> T transaction(TransactionWork<T> work) throws SQLException, EventException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (EventException | SQLException ex) {
                connection.rollback();
                throw ex;
            } catch (Exception ex) {
                connection.rollback();
                throw new SQLException("event_transaction_failed", ex);
            }
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T run(Connection connection) throws Exception;
    }

    public record EventInput(String gameId, String date, String time, String location,
                             int maxParticipants, String description) {}
    public record CompletionInput(String winnerId, int duration, String notes,
                                  String initialComment, String photoUrl) {}
    public record RequestMetadata(String ipAddress, String userAgent) {}
    public record JoinResult(EventData event, boolean waitlisted) {}
    public record LeaveResult(EventData event, boolean promoted) {}

    public static final class EventException extends Exception {
        public enum Kind { FORBIDDEN, NOT_FOUND, CONFLICT, INVALID }
        private final Kind kind;
        private final String reason;

        private EventException(Kind kind, String reason) {
            super(reason);
            this.kind = kind;
            this.reason = reason;
        }

        public Kind kind() { return kind; }
        public String reason() { return reason; }
        public static EventException forbidden(String reason) { return new EventException(Kind.FORBIDDEN, reason); }
        public static EventException notFound(String reason) { return new EventException(Kind.NOT_FOUND, reason); }
        public static EventException conflict(String reason) { return new EventException(Kind.CONFLICT, reason); }
        public static EventException invalid(String reason) { return new EventException(Kind.INVALID, reason); }
    }
}

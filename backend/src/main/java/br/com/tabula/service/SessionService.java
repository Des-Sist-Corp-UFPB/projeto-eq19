package br.com.tabula.service;

import br.com.tabula.model.AuditAction;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.SessionRepository;
import br.com.tabula.repository.SessionRepository.SessionData;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SessionService {
    private final HikariDataSource dataSource;
    private final SessionRepository repository;
    private final AuditLogService audit;

    public SessionService(HikariDataSource dataSource, SessionRepository repository, AuditLogService audit) {
        this.dataSource = dataSource;
        this.repository = repository;
        this.audit = audit;
    }

    public List<SessionData> list() throws SQLException { return repository.findAll(); }

    public SessionData get(String id) throws SQLException, SessionException {
        return repository.findByExternalId(id)
                .orElseThrow(() -> SessionException.notFound("session_not_found"));
    }

    public SessionData create(AuthenticatedPrincipal actor, SessionInput input, RequestMetadata metadata)
            throws SQLException, SessionException {
        validate(input);
        return transaction(connection -> {
            long gameId = required(() -> repository.requireGameId(connection, input.gameId()));
            LinkedHashSet<String> externalParticipants = new LinkedHashSet<>(input.participantIds());
            externalParticipants.add(actor.getExternalId());
            List<Long> participants = new ArrayList<>();
            for (String participant : externalParticipants) {
                participants.add(required(() -> repository.requireUserId(connection, participant)));
            }
            Long winnerId = null;
            if (input.winnerId() != null && !input.winnerId().isBlank()) {
                if (!externalParticipants.contains(input.winnerId())) {
                    throw SessionException.invalid("winner_not_participant");
                }
                winnerId = required(() -> repository.requireUserId(connection, input.winnerId()));
            }
            String externalId = "s_" + UUID.randomUUID();
            SessionData created = repository.insert(connection, externalId, gameId, parseDate(input.date()),
                    input.location().trim(), actor.getDatabaseId(), winnerId, input.duration(),
                    clean(input.notes()), participants);
            audit.record(connection, actor, AuditAction.SESSION_CREATED, "SESSION", externalId, true,
                    metadata.ipAddress(), metadata.userAgent(), Map.of());
            return created;
        });
    }

    public void delete(AuthenticatedPrincipal actor, String id, RequestMetadata metadata)
            throws SQLException, SessionException {
        transaction(connection -> {
            SessionData session = repository.findByExternalId(connection, id, true)
                    .orElseThrow(() -> SessionException.notFound("session_not_found"));
            if (session.organizerDatabaseId() != actor.getDatabaseId() && !actor.isAdmin()) {
                throw SessionException.forbidden("not_session_owner");
            }
            repository.delete(connection, session.databaseId());
            audit.record(connection, actor, AuditAction.SESSION_DELETED, "SESSION", id, true,
                    metadata.ipAddress(), metadata.userAgent(), Map.of());
            return null;
        });
    }

    public void auditRejected(AuthenticatedPrincipal actor, String id, String reason, RequestMetadata metadata) {
        audit.recordBestEffort(actor, AuditAction.SESSION_OPERATION_REJECTED, "SESSION", id, false,
                metadata.ipAddress(), metadata.userAgent(), Map.of("reason", reason));
    }

    private static void validate(SessionInput input) throws SessionException {
        if (input == null || blank(input.gameId()) || blank(input.date()) || blank(input.location())
                || input.participantIds() == null || input.participantIds().isEmpty()
                || input.participantIds().stream().anyMatch(SessionService::blank)
                || new LinkedHashSet<>(input.participantIds()).size() != input.participantIds().size()
                || input.duration() < 0 || input.duration() > 1440 || input.location().length() > 512) {
            throw SessionException.invalid("invalid_session");
        }
        parseDate(input.date());
    }

    private static LocalDateTime parseDate(String value) throws SessionException {
        try { return LocalDateTime.parse(value); }
        catch (DateTimeParseException ex) { throw SessionException.invalid("invalid_date"); }
    }

    private long required(SqlLong work) throws SQLException, SessionException {
        try { return work.get(); }
        catch (SQLException ex) {
            if ("game_not_found".equals(ex.getMessage()) || "user_not_found".equals(ex.getMessage())) {
                throw SessionException.notFound(ex.getMessage());
            }
            throw ex;
        }
    }

    private <T> T transaction(Work<T> work) throws SQLException, SessionException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (Exception ex) {
                connection.rollback();
                if (ex instanceof SessionException sessionException) throw sessionException;
                if (ex instanceof SQLException sqlException) {
                    if ("23505".equals(sqlException.getSQLState())) throw SessionException.conflict("duplicate_session");
                    throw sqlException;
                }
                throw new SQLException("Session transaction failed", ex);
            }
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String clean(String value) { return blank(value) ? null : value.trim(); }

    public record SessionInput(String gameId, String date, String location, List<String> participantIds,
                               String winnerId, int duration, String notes) {}
    public record RequestMetadata(String ipAddress, String userAgent) {}
    @FunctionalInterface private interface Work<T> { T run(Connection connection) throws Exception; }
    @FunctionalInterface private interface SqlLong { long get() throws SQLException; }

    public static final class SessionException extends Exception {
        public enum Kind { FORBIDDEN, NOT_FOUND, CONFLICT, INVALID }
        private final Kind kind;
        private final String reason;
        private SessionException(Kind kind, String reason) { this.kind = kind; this.reason = reason; }
        public Kind kind() { return kind; }
        public String reason() { return reason; }
        public static SessionException forbidden(String reason) { return new SessionException(Kind.FORBIDDEN, reason); }
        public static SessionException notFound(String reason) { return new SessionException(Kind.NOT_FOUND, reason); }
        public static SessionException conflict(String reason) { return new SessionException(Kind.CONFLICT, reason); }
        public static SessionException invalid(String reason) { return new SessionException(Kind.INVALID, reason); }
    }
}

package br.com.tabula.service;

import br.com.tabula.model.AuditAction;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.FavoriteRepository;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public final class FavoriteService {
    private final HikariDataSource dataSource;
    private final FavoriteRepository repository;
    private final AuditLogService audit;

    public FavoriteService(HikariDataSource dataSource, FavoriteRepository repository, AuditLogService audit) {
        this.dataSource = dataSource; this.repository = repository; this.audit = audit;
    }

    public List<String> list(AuthenticatedPrincipal actor) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return repository.findGameIds(connection, actor.getDatabaseId());
        }
    }

    public AddResult add(AuthenticatedPrincipal actor, String gameId, RequestMetadata metadata)
            throws SQLException, FavoriteException {
        return transaction(connection -> {
            long databaseGameId = requireGame(connection, gameId);
            boolean created = repository.insert(connection, actor.getDatabaseId(), databaseGameId);
            if (created) audit.record(connection, actor, AuditAction.FAVORITE_ADDED, "GAME", gameId, true,
                    metadata.ipAddress(), metadata.userAgent(), Map.of("targetId", gameId, "targetType", "GAME"));
            return new AddResult(gameId, created);
        });
    }

    public boolean remove(AuthenticatedPrincipal actor, String gameId, RequestMetadata metadata)
            throws SQLException, FavoriteException {
        return transaction(connection -> {
            long databaseGameId = requireGame(connection, gameId);
            boolean removed = repository.delete(connection, actor.getDatabaseId(), databaseGameId);
            if (removed) audit.record(connection, actor, AuditAction.FAVORITE_REMOVED, "GAME", gameId, true,
                    metadata.ipAddress(), metadata.userAgent(), Map.of("targetId", gameId, "targetType", "GAME"));
            return removed;
        });
    }

    public void auditRejected(AuthenticatedPrincipal actor, String gameId, String reason, RequestMetadata metadata) {
        audit.recordBestEffort(actor, AuditAction.FAVORITE_OPERATION_REJECTED, "GAME", gameId, false,
                metadata.ipAddress(), metadata.userAgent(),
                Map.of("reasonCode", reason, "targetId", gameId == null ? "" : gameId, "targetType", "GAME"));
    }

    private long requireGame(Connection connection, String gameId) throws SQLException, FavoriteException {
        if (gameId == null || gameId.isBlank() || gameId.length() > 80) throw FavoriteException.invalid("invalid_game_id");
        try { return repository.requireGameId(connection, gameId); }
        catch (SQLException ex) {
            if ("game_not_found".equals(ex.getMessage())) throw FavoriteException.notFound(ex.getMessage());
            throw ex;
        }
    }

    private <T> T transaction(Work<T> work) throws SQLException, FavoriteException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try { T result = work.run(connection); connection.commit(); return result; }
            catch (Exception ex) {
                connection.rollback();
                if (ex instanceof FavoriteException fe) throw fe;
                if (ex instanceof SQLException se) throw se;
                throw new SQLException("Favorite transaction failed", ex);
            }
        }
    }

    public record AddResult(String gameId, boolean created) {}
    public record RequestMetadata(String ipAddress, String userAgent) {}
    @FunctionalInterface private interface Work<T> { T run(Connection connection) throws Exception; }
    public static final class FavoriteException extends Exception {
        public enum Kind { NOT_FOUND, INVALID }
        private final Kind kind; private final String reason;
        private FavoriteException(Kind kind, String reason) { this.kind = kind; this.reason = reason; }
        public Kind kind() { return kind; } public String reason() { return reason; }
        static FavoriteException notFound(String reason) { return new FavoriteException(Kind.NOT_FOUND, reason); }
        static FavoriteException invalid(String reason) { return new FavoriteException(Kind.INVALID, reason); }
    }
}

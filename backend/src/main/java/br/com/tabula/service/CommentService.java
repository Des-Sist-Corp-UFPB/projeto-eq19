package br.com.tabula.service;

import br.com.tabula.model.AuditAction;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.CommentRepository;
import br.com.tabula.repository.CommentRepository.CommentData;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CommentService {
    private static final int MAX_CONTENT_LENGTH = 4000;
    private final HikariDataSource dataSource;
    private final CommentRepository repository;
    private final AuditLogService audit;

    public CommentService(HikariDataSource dataSource, CommentRepository repository, AuditLogService audit) {
        this.dataSource = dataSource;
        this.repository = repository;
        this.audit = audit;
    }

    public List<CommentData> list(String sessionId) throws SQLException, CommentException {
        try (Connection connection = dataSource.getConnection()) {
            long id = requireSession(connection, sessionId);
            return repository.findBySession(connection, id);
        }
    }

    public CommentData create(AuthenticatedPrincipal actor, String sessionId, String content,
                              RequestMetadata metadata) throws SQLException, CommentException {
        String cleanContent = content == null ? null : content.trim();
        if (cleanContent == null || cleanContent.isEmpty() || cleanContent.length() > MAX_CONTENT_LENGTH) {
            throw CommentException.invalid("invalid_comment_content");
        }
        return transaction(connection -> {
            long parentId = requireSession(connection, sessionId);
            String externalId = "c_" + UUID.randomUUID();
            CommentData created = repository.insert(connection, externalId, parentId,
                    actor.getDatabaseId(), cleanContent);
            audit.record(connection, actor, AuditAction.COMMENT_CREATED, "COMMENT", externalId, true,
                    metadata.ipAddress(), metadata.userAgent(),
                    Map.of("parentId", sessionId, "parentType", "SESSION"));
            return created;
        });
    }

    public void delete(AuthenticatedPrincipal actor, String sessionId, String commentId,
                       RequestMetadata metadata) throws SQLException, CommentException {
        transaction(connection -> {
            long parentId = requireSession(connection, sessionId);
            CommentData comment = repository.findById(connection, parentId, commentId, true)
                    .orElseThrow(() -> CommentException.notFound("comment_not_found"));
            if ((comment.authorDatabaseId() == null || comment.authorDatabaseId() != actor.getDatabaseId())
                    && !actor.isAdmin()) {
                throw CommentException.forbidden("not_comment_author");
            }
            repository.delete(connection, comment.databaseId());
            audit.record(connection, actor, AuditAction.COMMENT_DELETED, "COMMENT", commentId, true,
                    metadata.ipAddress(), metadata.userAgent(),
                    Map.of("parentId", sessionId, "parentType", "SESSION"));
            return null;
        });
    }

    public void auditRejected(AuthenticatedPrincipal actor, String sessionId, String commentId,
                              String reason, RequestMetadata metadata) {
        audit.recordBestEffort(actor, AuditAction.COMMENT_OPERATION_REJECTED, "COMMENT", commentId, false,
                metadata.ipAddress(), metadata.userAgent(), Map.of(
                        "reasonCode", reason, "parentId", sessionId, "parentType", "SESSION"));
    }

    private long requireSession(Connection connection, String sessionId) throws SQLException, CommentException {
        try { return repository.requireSessionId(connection, sessionId); }
        catch (SQLException ex) {
            if ("session_not_found".equals(ex.getMessage())) throw CommentException.notFound(ex.getMessage());
            throw ex;
        }
    }

    private <T> T transaction(Work<T> work) throws SQLException, CommentException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (Exception ex) {
                connection.rollback();
                if (ex instanceof CommentException ce) throw ce;
                if (ex instanceof SQLException se) throw se;
                throw new SQLException("Comment transaction failed", ex);
            }
        }
    }

    public record RequestMetadata(String ipAddress, String userAgent) {}
    @FunctionalInterface private interface Work<T> { T run(Connection connection) throws Exception; }

    public static final class CommentException extends Exception {
        public enum Kind { FORBIDDEN, NOT_FOUND, INVALID }
        private final Kind kind;
        private final String reason;
        private CommentException(Kind kind, String reason) { this.kind = kind; this.reason = reason; }
        public Kind kind() { return kind; }
        public String reason() { return reason; }
        public static CommentException forbidden(String reason) { return new CommentException(Kind.FORBIDDEN, reason); }
        public static CommentException notFound(String reason) { return new CommentException(Kind.NOT_FOUND, reason); }
        public static CommentException invalid(String reason) { return new CommentException(Kind.INVALID, reason); }
    }
}

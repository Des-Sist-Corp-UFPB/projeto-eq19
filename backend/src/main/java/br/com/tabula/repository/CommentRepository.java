package br.com.tabula.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CommentRepository {
    public long requireSessionId(Connection connection, String externalId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM partidas WHERE external_id = ?")) {
            statement.setString(1, externalId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("session_not_found");
                return rows.getLong(1);
            }
        }
    }

    public CommentData insert(Connection connection, String externalId, long sessionId,
                              long authorId, String content) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO comentarios (external_id, partida_id, usuario_id, conteudo, criado_em)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                RETURNING id, criado_em
                """)) {
            statement.setString(1, externalId);
            statement.setLong(2, sessionId);
            statement.setLong(3, authorId);
            statement.setString(4, content);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("comment_insert_failed");
                return findById(connection, sessionId, externalId, false).orElseThrow();
            }
        }
    }

    public List<CommentData> findBySession(Connection connection, long sessionId) throws SQLException {
        List<CommentData> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(select() +
                " WHERE c.partida_id = ? ORDER BY c.criado_em, c.id")) {
            statement.setLong(1, sessionId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(map(rows));
            }
        }
        return result;
    }

    public Optional<CommentData> findById(Connection connection, long sessionId, String externalId,
                                          boolean lock) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(select() +
                " WHERE c.partida_id = ? AND c.external_id = ?" + (lock ? " FOR UPDATE OF c" : ""))) {
            statement.setLong(1, sessionId);
            statement.setString(2, externalId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    public void delete(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM comentarios WHERE id = ?")) {
            statement.setLong(1, id);
            if (statement.executeUpdate() != 1) throw new SQLException("comment_delete_conflict");
        }
    }

    private static String select() {
        return """
                SELECT c.id, c.external_id, c.usuario_id, u.external_id AS user_id,
                       u.nome AS user_name, c.conteudo, c.criado_em
                FROM comentarios c LEFT JOIN usuarios u ON u.id = c.usuario_id
                """;
    }

    private static CommentData map(ResultSet rows) throws SQLException {
        long authorId = rows.getLong("usuario_id");
        boolean authorMissing = rows.wasNull();
        return new CommentData(rows.getLong("id"), rows.getString("external_id"),
                authorMissing ? null : authorId, rows.getString("user_id"),
                rows.getString("user_name"), "🎲",
                rows.getString("conteudo"), rows.getTimestamp("criado_em").toLocalDateTime());
    }

    public record CommentData(long databaseId, String externalId, Long authorDatabaseId,
                              String userId, String userName, String userAvatar,
                              String content, LocalDateTime createdAt) {}
}

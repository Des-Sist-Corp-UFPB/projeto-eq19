package br.com.tabula.repository;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SessionRepository {
    private final HikariDataSource dataSource;

    public SessionRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<SessionData> findAll() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String sql = baseSelect() + " ORDER BY p.data_hora DESC, p.id DESC LIMIT 500";
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rows = statement.executeQuery()) {
                List<SessionData> result = new ArrayList<>();
                while (rows.next()) result.add(map(connection, rows));
                return result;
            }
        }
    }

    public Optional<SessionData> findByExternalId(String id) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return findByExternalId(connection, id, false);
        }
    }

    public Optional<SessionData> findByExternalId(Connection connection, String id, boolean lock)
            throws SQLException {
        String sql = baseSelect() + " WHERE p.external_id = ?" + (lock ? " FOR UPDATE OF p" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(connection, rows)) : Optional.empty();
            }
        }
    }

    public long requireGameId(Connection connection, String externalId) throws SQLException {
        return requireId(connection, "SELECT id FROM jogos WHERE external_id = ?", externalId, "game_not_found");
    }

    public long requireUserId(Connection connection, String externalId) throws SQLException {
        return requireId(connection, "SELECT id FROM usuarios WHERE external_id = ?", externalId, "user_not_found");
    }

    public SessionData insert(Connection connection, String externalId, long gameId,
                              LocalDateTime dateTime, String location, long organizerId,
                              Long winnerId, int duration, String notes,
                              List<Long> participants) throws SQLException {
        long id;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO partidas
                    (external_id, jogo_id, data_hora, local, organizador_id, vencedor_id,
                     duracao_minutos, notas, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, externalId);
            statement.setLong(2, gameId);
            statement.setTimestamp(3, Timestamp.valueOf(dateTime));
            statement.setString(4, location);
            statement.setLong(5, organizerId);
            if (winnerId == null) statement.setNull(6, Types.BIGINT); else statement.setLong(6, winnerId);
            statement.setInt(7, duration);
            statement.setString(8, notes);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("session_insert_failed");
                id = keys.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO partida_participantes (partida_id, usuario_id) VALUES (?, ?)")) {
            for (Long participant : participants) {
                statement.setLong(1, id);
                statement.setLong(2, participant);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        return findByExternalId(connection, externalId, false).orElseThrow();
    }

    public void delete(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM partidas WHERE id = ?")) {
            statement.setLong(1, id);
            if (statement.executeUpdate() != 1) throw new SQLException("session_delete_conflict");
        }
    }

    private SessionData map(Connection connection, ResultSet row) throws SQLException {
        long id = row.getLong("id");
        List<String> participants = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT u.external_id
                FROM partida_participantes pp
                JOIN usuarios u ON u.id = pp.usuario_id
                WHERE pp.partida_id = ?
                ORDER BY u.external_id
                """)) {
            statement.setLong(1, id);
            try (ResultSet members = statement.executeQuery()) {
                while (members.next()) participants.add(members.getString(1));
            }
        }
        List<String> photos = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT url FROM partida_fotos WHERE partida_id = ? ORDER BY ordem, id
                """)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) photos.add(rows.getString(1));
            }
        }
        List<CommentData> comments = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT c.external_id, u.external_id AS user_id, u.nome, c.conteudo, c.criado_em
                FROM comentarios c LEFT JOIN usuarios u ON u.id = c.usuario_id
                WHERE c.partida_id = ? ORDER BY c.criado_em, c.id
                """)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) comments.add(new CommentData(rows.getString("external_id"),
                        rows.getString("user_id"), rows.getString("nome"), rows.getString("conteudo"),
                        rows.getTimestamp("criado_em").toLocalDateTime()));
            }
        }
        return new SessionData(id, row.getString("external_id"), row.getString("game_external_id"),
                row.getTimestamp("data_hora").toLocalDateTime(), row.getString("local"),
                row.getLong("organizador_id"), row.getString("organizer_external_id"),
                row.getString("winner_external_id"), row.getInt("duracao_minutos"),
                row.getString("notas"), participants, photos, comments);
    }

    private static long requireId(Connection connection, String sql, String id, String error)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException(error);
                return rows.getLong(1);
            }
        }
    }

    private static String baseSelect() {
        return """
                SELECT p.id, p.external_id, p.data_hora, p.local, p.organizador_id,
                       p.duracao_minutos, p.notas, j.external_id AS game_external_id,
                       organizer.external_id AS organizer_external_id,
                       winner.external_id AS winner_external_id
                FROM partidas p
                JOIN jogos j ON j.id = p.jogo_id
                JOIN usuarios organizer ON organizer.id = p.organizador_id
                LEFT JOIN usuarios winner ON winner.id = p.vencedor_id
                """;
    }

    public record SessionData(long databaseId, String externalId, String gameId,
                              LocalDateTime dateTime, String location,
                              long organizerDatabaseId, String organizerId, String winnerId,
                              int duration, String notes, List<String> participantIds,
                              List<String> photos, List<CommentData> comments) {}
    public record CommentData(String id, String userId, String userName, String content,
                              LocalDateTime createdAt) {}
}

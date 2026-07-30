package br.com.tabula.repository;

import br.com.tabula.model.AuthenticatedPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EventRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HikariDataSource dataSource;

    public EventRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<EventData> findAll() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return findAll(connection);
        }
    }

    public String findAllAsStateJson() throws SQLException {
        String sql = """
                SELECT COALESCE(jsonb_agg(event_json ORDER BY event_time, event_id), '[]'::jsonb)::text
                FROM (
                    SELECT e.id AS event_id, e.data_hora AS event_time,
                           jsonb_build_object(
                               'id', e.external_id,
                               'gameId', j.external_id,
                               'date', to_char(e.data_hora, 'YYYY-MM-DD'),
                               'time', to_char(e.data_hora, 'HH24:MI'),
                               'location', e.local,
                               'maxParticipants', e.max_participantes,
                               'participantIds', COALESCE((
                                   SELECT jsonb_agg(u.external_id ORDER BY ep.inscrito_em, ep.usuario_id)
                                   FROM evento_participantes ep
                                   JOIN usuarios u ON u.id = ep.usuario_id
                                   WHERE ep.evento_id = e.id AND ep.tipo = 'PARTICIPANT'
                               ), '[]'::jsonb),
                               'waitingListIds', COALESCE((
                                   SELECT jsonb_agg(u.external_id ORDER BY ep.ordem_fila, ep.inscrito_em, ep.usuario_id)
                                   FROM evento_participantes ep
                                   JOIN usuarios u ON u.id = ep.usuario_id
                                   WHERE ep.evento_id = e.id AND ep.tipo = 'WAITING'
                               ), '[]'::jsonb),
                               'description', COALESCE(e.descricao, ''),
                               'organizerId', organizer.external_id,
                               'status', e.status
                           ) AS event_json
                    FROM eventos e
                    JOIN jogos j ON j.id = e.jogo_id
                    JOIN usuarios organizer ON organizer.id = e.organizador_id
                ) projected
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getString(1) : "[]";
        }
    }

    public List<EventData> findAll(Connection connection) throws SQLException {
        String sql = baseSelect() + " ORDER BY e.data_hora, e.id";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<EventData> events = new ArrayList<>();
            while (resultSet.next()) events.add(map(connection, resultSet));
            return events;
        }
    }

    public Optional<EventData> findByExternalId(String externalId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return findByExternalId(connection, externalId, false);
        }
    }

    public Optional<EventData> findByExternalId(Connection connection, String externalId, boolean forUpdate)
            throws SQLException {
        String sql = baseSelect() + " WHERE e.external_id = ?" + (forUpdate ? " FOR UPDATE OF e" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, externalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(connection, resultSet)) : Optional.empty();
            }
        }
    }

    public long requireGameId(Connection connection, String externalId) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT id FROM jogos WHERE external_id = ?")) {
            statement.setString(1, externalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new SQLException("game_not_found");
                return resultSet.getLong(1);
            }
        }
    }

    public EventData insert(Connection connection, String externalId, long gameId, LocalDateTime dateTime,
                            String location, String description, int maxParticipants,
                            AuthenticatedPrincipal organizer) throws SQLException {
        String sql = """
                INSERT INTO eventos
                    (external_id, jogo_id, data_hora, local, descricao, max_participantes,
                     status, organizador_id, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, ?, ?, 'active', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;
        long eventId;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, externalId);
            statement.setLong(2, gameId);
            statement.setTimestamp(3, Timestamp.valueOf(dateTime));
            statement.setString(4, location);
            statement.setString(5, description);
            statement.setInt(6, maxParticipants);
            statement.setLong(7, organizer.getDatabaseId());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("event_insert_failed");
                eventId = keys.getLong(1);
            }
        }
        insertMembership(connection, eventId, organizer.getDatabaseId(), "PARTICIPANT", null);
        return findByExternalId(connection, externalId, false).orElseThrow();
    }

    public void update(Connection connection, long eventId, long gameId, LocalDateTime dateTime,
                       String location, String description, int maxParticipants) throws SQLException {
        String sql = """
                UPDATE eventos SET jogo_id = ?, data_hora = ?, local = ?, descricao = ?,
                    max_participantes = ?, atualizado_em = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, gameId);
            statement.setTimestamp(2, Timestamp.valueOf(dateTime));
            statement.setString(3, location);
            statement.setString(4, description);
            statement.setInt(5, maxParticipants);
            statement.setLong(6, eventId);
            statement.executeUpdate();
        }
    }

    public void updateStatus(Connection connection, long eventId, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE eventos SET status = ?, atualizado_em = CURRENT_TIMESTAMP WHERE id = ?")) {
            statement.setString(1, status);
            statement.setLong(2, eventId);
            statement.executeUpdate();
        }
    }

    public boolean hasMembership(Connection connection, long eventId, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM evento_participantes WHERE evento_id = ? AND usuario_id = ?")) {
            statement.setLong(1, eventId);
            statement.setLong(2, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public int participantCount(Connection connection, long eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM evento_participantes WHERE evento_id = ? AND tipo = 'PARTICIPANT'")) {
            statement.setLong(1, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    public void insertMembership(Connection connection, long eventId, long userId, String type, Long queueOrder)
            throws SQLException {
        String sql = """
                INSERT INTO evento_participantes (evento_id, usuario_id, tipo, ordem_fila)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            statement.setLong(2, userId);
            statement.setString(3, type);
            if (queueOrder == null) statement.setNull(4, java.sql.Types.BIGINT);
            else statement.setLong(4, queueOrder);
            statement.executeUpdate();
        }
    }

    public long nextQueueOrder(Connection connection, long eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(MAX(ordem_fila), 0) + 1 FROM evento_participantes WHERE evento_id = ?")) {
            statement.setLong(1, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    public Optional<Membership> membership(Connection connection, long eventId, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT tipo, ordem_fila FROM evento_participantes WHERE evento_id = ? AND usuario_id = ?")) {
            statement.setLong(1, eventId);
            statement.setLong(2, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(new Membership(resultSet.getString(1), (Long) resultSet.getObject(2)))
                        : Optional.empty();
            }
        }
    }

    public void deleteMembership(Connection connection, long eventId, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM evento_participantes WHERE evento_id = ? AND usuario_id = ?")) {
            statement.setLong(1, eventId);
            statement.setLong(2, userId);
            statement.executeUpdate();
        }
    }

    public Optional<Long> promoteFirstWaiting(Connection connection, long eventId) throws SQLException {
        String sql = """
                SELECT usuario_id FROM evento_participantes
                WHERE evento_id = ? AND tipo = 'WAITING'
                ORDER BY ordem_fila, inscrito_em, usuario_id
                LIMIT 1 FOR UPDATE
                """;
        Long userId = null;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) userId = resultSet.getLong(1);
            }
        }
        if (userId == null) return Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE evento_participantes SET tipo = 'PARTICIPANT', ordem_fila = NULL
                WHERE evento_id = ? AND usuario_id = ?
                """)) {
            statement.setLong(1, eventId);
            statement.setLong(2, userId);
            statement.executeUpdate();
        }
        return Optional.of(userId);
    }

    public void createSession(Connection connection, EventData event, String sessionExternalId,
                              Long winnerDatabaseId, int duration, String notes,
                              String initialComment, String photoUrl) throws SQLException {
        long sessionId;
        String insert = """
                INSERT INTO partidas
                    (external_id, jogo_id, evento_id, data_hora, local, organizador_id,
                     vencedor_id, duracao_minutos, notas, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;
        try (PreparedStatement statement = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, sessionExternalId);
            statement.setLong(2, event.gameDatabaseId());
            statement.setLong(3, event.databaseId());
            statement.setTimestamp(4, Timestamp.valueOf(event.dateTime()));
            statement.setString(5, event.location());
            statement.setLong(6, event.organizerDatabaseId());
            if (winnerDatabaseId == null) statement.setNull(7, java.sql.Types.BIGINT);
            else statement.setLong(7, winnerDatabaseId);
            statement.setInt(8, duration);
            statement.setString(9, notes);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("session_insert_failed");
                sessionId = keys.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO partida_participantes (partida_id, usuario_id)
                SELECT ?, usuario_id FROM evento_participantes
                WHERE evento_id = ? AND tipo = 'PARTICIPANT'
                """)) {
            statement.setLong(1, sessionId);
            statement.setLong(2, event.databaseId());
            statement.executeUpdate();
        }
        if (initialComment != null && !initialComment.isBlank()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO comentarios (external_id, partida_id, usuario_id, conteudo, criado_em)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """)) {
                statement.setString(1, "c_" + java.util.UUID.randomUUID());
                statement.setLong(2, sessionId);
                statement.setLong(3, event.organizerDatabaseId());
                statement.setString(4, initialComment);
                statement.executeUpdate();
            }
        }
        if (photoUrl != null && !photoUrl.isBlank()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO partida_fotos (partida_id, url, ordem, criado_em)
                    VALUES (?, ?, 1, CURRENT_TIMESTAMP)
                    """)) {
                statement.setLong(1, sessionId);
                statement.setString(2, photoUrl);
                statement.executeUpdate();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE app_state
                SET data = jsonb_set(
                    data,
                    '{sessions}',
                    jsonb_build_array(jsonb_build_object(
                        'id', ?,
                        'gameId', ?,
                        'date', to_char(?::timestamp, 'YYYY-MM-DD"T"HH24:MI:SS'),
                        'location', ?,
                        'organizerId', ?,
                        'participantIds', to_jsonb(?::text[]),
                        'winnerId', ?,
                        'duration', ?,
                        'notes', COALESCE(?, ''),
                        'photos', to_jsonb(?::text[]),
                        'comments', ?::jsonb
                    )) || COALESCE(data->'sessions', '[]'::jsonb),
                    true
                ),
                updated_at = CURRENT_TIMESTAMP
                WHERE id = 1
                """)) {
            statement.setString(1, sessionExternalId);
            statement.setString(2, event.gameExternalId());
            statement.setTimestamp(3, Timestamp.valueOf(event.dateTime()));
            statement.setString(4, event.location());
            statement.setString(5, event.organizerExternalId());
            statement.setArray(6, connection.createArrayOf("text", event.participantIds().toArray()));
            statement.setString(7, winnerDatabaseId == null ? null : externalUserId(connection, winnerDatabaseId));
            statement.setInt(8, duration);
            statement.setString(9, notes);
            statement.setArray(10, connection.createArrayOf("text",
                    photoUrl == null || photoUrl.isBlank() ? new String[0] : new String[]{photoUrl}));
            statement.setString(11, legacyCommentsJson(event, initialComment));
            statement.executeUpdate();
        }
    }

    private String externalUserId(Connection connection, long databaseId) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT external_id FROM usuarios WHERE id = ?")) {
            statement.setLong(1, databaseId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private static String legacyCommentsJson(EventData event, String comment) {
        if (comment == null || comment.isBlank()) return "[]";
        try {
            return JSON.writeValueAsString(List.of(Map.of(
                    "id", "c_" + java.util.UUID.randomUUID(),
                    "userId", event.organizerExternalId(),
                    "userName", "",
                    "userAvatar", "",
                    "content", comment,
                    "createdAt", java.time.Instant.now().toString()
            )));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return "[]";
        }
    }

    public Long findUserDatabaseId(Connection connection, String externalId) throws SQLException {
        if (externalId == null) return null;
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT id FROM usuarios WHERE external_id = ?")) {
            statement.setString(1, externalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : null;
            }
        }
    }

    private EventData map(Connection connection, ResultSet resultSet) throws SQLException {
        long eventId = resultSet.getLong("id");
        List<String> participants = new ArrayList<>();
        List<String> waiting = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT u.external_id, ep.tipo
                FROM evento_participantes ep
                JOIN usuarios u ON u.id = ep.usuario_id
                WHERE ep.evento_id = ?
                ORDER BY CASE ep.tipo WHEN 'PARTICIPANT' THEN 0 ELSE 1 END,
                         ep.ordem_fila NULLS FIRST, ep.inscrito_em, ep.usuario_id
                """)) {
            statement.setLong(1, eventId);
            try (ResultSet members = statement.executeQuery()) {
                while (members.next()) {
                    if ("WAITING".equals(members.getString("tipo"))) waiting.add(members.getString("external_id"));
                    else participants.add(members.getString("external_id"));
                }
            }
        }
        return new EventData(eventId, resultSet.getString("external_id"),
                resultSet.getLong("jogo_id"), resultSet.getString("game_external_id"),
                resultSet.getTimestamp("data_hora").toLocalDateTime(),
                resultSet.getString("local"), resultSet.getString("descricao"),
                resultSet.getInt("max_participantes"), resultSet.getString("status"),
                resultSet.getLong("organizador_id"), resultSet.getString("organizer_external_id"),
                participants, waiting);
    }

    private static String baseSelect() {
        return """
                SELECT e.id, e.external_id, e.jogo_id, j.external_id AS game_external_id,
                       e.data_hora, e.local, e.descricao, e.max_participantes, e.status,
                       e.organizador_id, u.external_id AS organizer_external_id
                FROM eventos e
                JOIN jogos j ON j.id = e.jogo_id
                JOIN usuarios u ON u.id = e.organizador_id
                """;
    }

    public record Membership(String type, Long queueOrder) {}

    public record EventData(
            long databaseId,
            String externalId,
            long gameDatabaseId,
            String gameExternalId,
            LocalDateTime dateTime,
            String location,
            String description,
            int maxParticipants,
            String status,
            long organizerDatabaseId,
            String organizerExternalId,
            List<String> participantIds,
            List<String> waitingListIds
    ) {}
}

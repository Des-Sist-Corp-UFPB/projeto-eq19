package br.com.tabula.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

public class RelationalStateSyncService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelationalStateSyncService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RelationalStateSyncService() {
    }

    @WithSpan("sync-from-state-json")
    public static void syncFromStateJson(HikariDataSource dataSource, String payload) throws Exception {
        syncFromStateJson(dataSource, payload, false);
    }

    public static void syncFromStateJson(HikariDataSource dataSource, String payload, boolean bootstrapEvents)
            throws Exception {
        if (payload == null || payload.isBlank()) {
            LOGGER.warn("RelationalStateSyncService: Empty or blank payload provided.");
            return;
        }

        JsonNode root = MAPPER.readTree(payload);

        int usersSynced = 0;
        int gamesSynced = 0;
        int eventsSynced = 0;
        int sessionsSynced = 0;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1) Delete dependent rows in safe dependency order
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM favoritos")) {
                    stmt.executeUpdate();
                }
                if (bootstrapEvents) {
                    // Bootstrap is the only explicit legacy-import path. Normal
                    // shadow synchronization never mutates authoritative sessions.
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM comentarios")) {
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM partida_fotos")) {
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM partida_participantes")) {
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM partidas")) {
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM evento_participantes")) {
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM eventos")) {
                        stmt.executeUpdate();
                    }
                }
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM logs")) {
                    stmt.executeUpdate();
                }

                // 2) Sync users (do not delete usuarios)
                JsonNode usersNode = root.path("users");
                if (usersNode.isArray()) {
                    for (JsonNode userNode : usersNode) {
                        String userId = userNode.path("id").asText();
                        String name = userNode.path("name").asText();
                        String email = userNode.path("email").asText();
                        String roleStr = userNode.path("role").asText();
                        String role = "admin".equalsIgnoreCase(roleStr) ? "ADMIN" : "USER";
                        String passwordHash = userNode.has("passwordHash") && !userNode.path("passwordHash").isNull()
                                ? userNode.path("passwordHash").asText() : null;
                        String avatarUrl = userNode.has("avatarUrl") && !userNode.path("avatarUrl").isNull()
                                ? userNode.path("avatarUrl").asText() : null;
                        String bio = userNode.has("bio") && !userNode.path("bio").isNull()
                                ? userNode.path("bio").asText() : null;
                        String course = userNode.has("course") && !userNode.path("course").isNull()
                                ? userNode.path("course").asText() : null;

                        // Check if user exists by external_id
                        Long dbIdByExt = null;
                        String dbEmailByExt = null;
                        try (PreparedStatement selExt = conn.prepareStatement("SELECT id, email FROM usuarios WHERE external_id = ?")) {
                            selExt.setString(1, userId);
                            try (ResultSet rs = selExt.executeQuery()) {
                                if (rs.next()) {
                                    dbIdByExt = rs.getLong("id");
                                    dbEmailByExt = rs.getString("email");
                                }
                            }
                        }

                        if (dbIdByExt != null) {
                            // User exists by external_id
                            // Check if new email is safe to update (not in use by another user)
                            boolean emailSafe = false;
                            if (email.equalsIgnoreCase(dbEmailByExt)) {
                                emailSafe = true;
                            } else {
                                try (PreparedStatement chkEmail = conn.prepareStatement("SELECT 1 FROM usuarios WHERE lower(email) = lower(?) AND id <> ?")) {
                                    chkEmail.setString(1, email);
                                    chkEmail.setLong(2, dbIdByExt);
                                    try (ResultSet rs = chkEmail.executeQuery()) {
                                        emailSafe = !rs.next();
                                    }
                                }
                                if (!emailSafe) {
                                    LOGGER.atWarn()
                                          .addKeyValue("user_id", userId)
                                          .addKeyValue("operation", "relational_state_sync")
                                          .addKeyValue("sync_result", "email_conflict")
                                          .log("Email update skipped during state synchronization");
                                }
                            }

                            String updateSql = emailSafe
                                    ? "UPDATE usuarios SET nome = ?, email = ?, role = ?, avatar_url = ?, bio = ?, curso = ?, atualizado_em = CURRENT_TIMESTAMP WHERE id = ?"
                                    : "UPDATE usuarios SET nome = ?, role = ?, avatar_url = ?, bio = ?, curso = ?, atualizado_em = CURRENT_TIMESTAMP WHERE id = ?";

                            try (PreparedStatement updStmt = conn.prepareStatement(updateSql)) {
                                updStmt.setString(1, name);
                                if (emailSafe) {
                                    updStmt.setString(2, email);
                                    updStmt.setString(3, role);
                                    updStmt.setString(4, avatarUrl);
                                    updStmt.setString(5, bio);
                                    updStmt.setString(6, course);
                                    updStmt.setLong(7, dbIdByExt);
                                } else {
                                    updStmt.setString(2, role);
                                    updStmt.setString(3, avatarUrl);
                                    updStmt.setString(4, bio);
                                    updStmt.setString(5, course);
                                    updStmt.setLong(6, dbIdByExt);
                                }
                                updStmt.executeUpdate();
                            }
                        } else {
                            // Try to find user by email (case-insensitive)
                            Long dbIdByEmail = null;
                            try (PreparedStatement selEmail = conn.prepareStatement("SELECT id FROM usuarios WHERE lower(email) = lower(?)")) {
                                selEmail.setString(1, email);
                                try (ResultSet rs = selEmail.executeQuery()) {
                                    if (rs.next()) {
                                        dbIdByEmail = rs.getLong("id");
                                    }
                                }
                            }

                            if (dbIdByEmail != null) {
                                // Link user by setting external_id and update other profile fields
                                String updateSql = "UPDATE usuarios SET external_id = ?, nome = ?, role = ?, avatar_url = ?, bio = ?, curso = ?, atualizado_em = CURRENT_TIMESTAMP WHERE id = ?";
                                try (PreparedStatement updStmt = conn.prepareStatement(updateSql)) {
                                    updStmt.setString(1, userId);
                                    updStmt.setString(2, name);
                                    updStmt.setString(3, role);
                                    updStmt.setString(4, avatarUrl);
                                    updStmt.setString(5, bio);
                                    updStmt.setString(6, course);
                                    updStmt.setLong(7, dbIdByEmail);
                                    updStmt.executeUpdate();
                                }
                            } else {
                                // Insert new user
                                String insertSql = """
                                        INSERT INTO usuarios (external_id, nome, email, senha_hash, role, email_verificado, avatar_url, bio, curso, criado_em)
                                        VALUES (?, ?, ?, ?, ?, TRUE, ?, ?, ?, CURRENT_TIMESTAMP)
                                        """;
                                try (PreparedStatement insStmt = conn.prepareStatement(insertSql)) {
                                    insStmt.setString(1, userId);
                                    insStmt.setString(2, name);
                                    insStmt.setString(3, email);
                                    insStmt.setString(4, passwordHash != null ? passwordHash : "DISABLED");
                                    insStmt.setString(5, role);
                                    insStmt.setString(6, avatarUrl);
                                    insStmt.setString(7, bio);
                                    insStmt.setString(8, course);
                                    insStmt.executeUpdate();
                                }
                            }
                        }
                        usersSynced++;
                    }
                }

                // Cache all users external_id to internal id
                Map<String, Long> userExternalToInternalId = new HashMap<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT id, external_id FROM usuarios WHERE external_id IS NOT NULL")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            userExternalToInternalId.put(rs.getString("external_id"), rs.getLong("id"));
                        }
                    }
                }

                // 3) Sync boardGames
                JsonNode gamesNode = root.path("boardGames");
                if (gamesNode.isArray()) {
                    for (JsonNode gameNode : gamesNode) {
                        String gameId = gameNode.path("id").asText();
                        String name = gameNode.path("name").asText();
                        String description = gameNode.path("description").asText();
                        String coverUrl = gameNode.path("coverUrl").asText();
                        String category = gameNode.path("category").asText();

                        Integer minPlayers = gameNode.has("minPlayers") && !gameNode.path("minPlayers").isNull()
                                ? gameNode.path("minPlayers").asInt() : null;
                        Integer maxPlayers = gameNode.has("maxPlayers") && !gameNode.path("maxPlayers").isNull()
                                ? gameNode.path("maxPlayers").asInt() : null;
                        Integer avgPlayTime = gameNode.has("avgPlayTime") && !gameNode.path("avgPlayTime").isNull()
                                ? gameNode.path("avgPlayTime").asInt() : null;
                        Double complexity = gameNode.has("complexity") && !gameNode.path("complexity").isNull()
                                ? gameNode.path("complexity").asDouble() : null;

                        // Find by external_id first
                        Long dbGameId = null;
                        try (PreparedStatement selExt = conn.prepareStatement("SELECT id FROM jogos WHERE external_id = ?")) {
                            selExt.setString(1, gameId);
                            try (ResultSet rs = selExt.executeQuery()) {
                                if (rs.next()) {
                                    dbGameId = rs.getLong("id");
                                }
                            }
                        }

                        if (dbGameId != null) {
                            // Update existing game by external_id
                            String updSql = """
                                    UPDATE jogos
                                    SET nome = ?, descricao = ?, cover_url = ?, categoria = ?, min_players = ?, max_players = ?, avg_play_time = ?, complexity = ?, atualizado_em = CURRENT_TIMESTAMP
                                    WHERE id = ?
                                    """;
                            try (PreparedStatement updStmt = conn.prepareStatement(updSql)) {
                                updStmt.setString(1, name);
                                updStmt.setString(2, description);
                                updStmt.setString(3, coverUrl);
                                updStmt.setString(4, category);
                                setNullableInt(updStmt, 5, minPlayers);
                                setNullableInt(updStmt, 6, maxPlayers);
                                setNullableInt(updStmt, 7, avgPlayTime);
                                setNullableDouble(updStmt, 8, complexity);
                                updStmt.setLong(9, dbGameId);
                                updStmt.executeUpdate();
                            }
                        } else {
                            // Find by name
                            Long dbGameIdByName = null;
                            try (PreparedStatement selName = conn.prepareStatement("SELECT id FROM jogos WHERE nome = ?")) {
                                selName.setString(1, name);
                                try (ResultSet rs = selName.executeQuery()) {
                                    if (rs.next()) {
                                        dbGameIdByName = rs.getLong("id");
                                    }
                                }
                            }

                            if (dbGameIdByName != null) {
                                // If external_id is null, link it, otherwise update.
                                String updSql = """
                                        UPDATE jogos
                                        SET external_id = ?, descricao = ?, cover_url = ?, categoria = ?, min_players = ?, max_players = ?, avg_play_time = ?, complexity = ?, atualizado_em = CURRENT_TIMESTAMP
                                        WHERE id = ?
                                        """;
                                try (PreparedStatement updStmt = conn.prepareStatement(updSql)) {
                                    updStmt.setString(1, gameId);
                                    updStmt.setString(2, description);
                                    updStmt.setString(3, coverUrl);
                                    updStmt.setString(4, category);
                                    setNullableInt(updStmt, 5, minPlayers);
                                    setNullableInt(updStmt, 6, maxPlayers);
                                    setNullableInt(updStmt, 7, avgPlayTime);
                                    setNullableDouble(updStmt, 8, complexity);
                                    updStmt.setLong(9, dbGameIdByName);
                                    updStmt.executeUpdate();
                                }
                            } else {
                                // Insert new game
                                String insSql = """
                                        INSERT INTO jogos (external_id, nome, descricao, cover_url, categoria, min_players, max_players, avg_play_time, complexity, atualizado_em)
                                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                                        """;
                                try (PreparedStatement insStmt = conn.prepareStatement(insSql)) {
                                    insStmt.setString(1, gameId);
                                    insStmt.setString(2, name);
                                    insStmt.setString(3, description);
                                    insStmt.setString(4, coverUrl);
                                    insStmt.setString(5, category);
                                    setNullableInt(insStmt, 6, minPlayers);
                                    setNullableInt(insStmt, 7, maxPlayers);
                                    setNullableInt(insStmt, 8, avgPlayTime);
                                    setNullableDouble(insStmt, 9, complexity);
                                    insStmt.executeUpdate();
                                }
                            }
                        }
                        gamesSynced++;
                    }
                }

                // Cache all games external_id to internal id
                Map<String, Long> gameExternalToInternalId = new HashMap<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT id, external_id FROM jogos WHERE external_id IS NOT NULL")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            gameExternalToInternalId.put(rs.getString("external_id"), rs.getLong("id"));
                        }
                    }
                }

                // 4) Sync events
                JsonNode eventsNode = root.path("events");
                if (bootstrapEvents && eventsNode.isArray()) {
                    String insEventSql = """
                            INSERT INTO eventos (external_id, jogo_id, data_hora, local, descricao, max_participantes, status, organizador_id, criado_em)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                            """;
                    try (PreparedStatement stmt = conn.prepareStatement(insEventSql, Statement.RETURN_GENERATED_KEYS)) {
                        for (JsonNode eventNode : eventsNode) {
                            String eventId = eventNode.path("id").asText();
                            stmt.setString(1, eventId);

                            String gameId = eventNode.path("gameId").asText();
                            Long dbGameId = gameExternalToInternalId.get(gameId);
                            setNullableLong(stmt, 2, dbGameId);

                            String date = eventNode.path("date").asText();
                            String time = eventNode.path("time").asText();
                            String dataHoraStr = (date != null && !date.isBlank() && time != null && !time.isBlank())
                                    ? date + "T" + time + ":00" : "";
                            stmt.setTimestamp(3, parseTimestamp(dataHoraStr));

                            stmt.setString(4, eventNode.path("location").asText());
                            stmt.setString(5, eventNode.path("description").asText());

                            Integer maxParts = eventNode.has("maxParticipants") && !eventNode.path("maxParticipants").isNull()
                                    ? eventNode.path("maxParticipants").asInt() : null;
                            setNullableInt(stmt, 6, maxParts);

                            String status = eventNode.path("status").asText();
                            if (status.isBlank()) {
                                status = "active";
                            }
                            stmt.setString(7, status);

                            String organizerId = eventNode.path("organizerId").asText();
                            Long dbOrganizerId = userExternalToInternalId.get(organizerId);
                            setNullableLong(stmt, 8, dbOrganizerId);

                            stmt.executeUpdate();

                            long dbEventId;
                            try (ResultSet gk = stmt.getGeneratedKeys()) {
                                if (gk.next()) {
                                    dbEventId = gk.getLong(1);
                                } else {
                                    throw new SQLException("Failed to get generated key for event: " + eventId);
                                }
                            }

                            // participantIds -> PARTICIPANT
                            JsonNode partsNode = eventNode.path("participantIds");
                            if (partsNode.isArray()) {
                                try (PreparedStatement pStmt = conn.prepareStatement(
                                        "INSERT INTO evento_participantes (evento_id, usuario_id, tipo) VALUES (?, ?, 'PARTICIPANT') ON CONFLICT DO NOTHING")) {
                                    for (JsonNode pNode : partsNode) {
                                        String pUserId = pNode.asText();
                                        Long dbPUserId = userExternalToInternalId.get(pUserId);
                                        if (dbPUserId != null) {
                                            pStmt.setLong(1, dbEventId);
                                            pStmt.setLong(2, dbPUserId);
                                            pStmt.executeUpdate();
                                        }
                                    }
                                }
                            }

                            // waitingListIds -> WAITING
                            JsonNode waitNode = eventNode.path("waitingListIds");
                            if (waitNode.isArray()) {
                                try (PreparedStatement wStmt = conn.prepareStatement(
                                        "INSERT INTO evento_participantes (evento_id, usuario_id, tipo) VALUES (?, ?, 'WAITING') ON CONFLICT DO NOTHING")) {
                                    for (JsonNode wtNode : waitNode) {
                                        String wUserId = wtNode.asText();
                                        Long dbWUserId = userExternalToInternalId.get(wUserId);
                                        if (dbWUserId != null) {
                                            wStmt.setLong(1, dbEventId);
                                            wStmt.setLong(2, dbWUserId);
                                            wStmt.executeUpdate();
                                        }
                                    }
                                }
                            }
                            eventsSynced++;
                        }
                    }
                }

                // 5) Sync sessions (partidas)
                JsonNode sessionsNode = root.path("sessions");
                if (bootstrapEvents && sessionsNode.isArray()) {
                    String insSessionSql = """
                            INSERT INTO partidas (external_id, jogo_id, evento_id, data_hora, local, organizador_id, vencedor_id, duracao_minutos, notas, criado_em)
                            VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                            """;
                    try (PreparedStatement stmt = conn.prepareStatement(insSessionSql, Statement.RETURN_GENERATED_KEYS)) {
                        for (JsonNode sessionNode : sessionsNode) {
                            String sessionId = sessionNode.path("id").asText();
                            stmt.setString(1, sessionId);

                            String gameId = sessionNode.path("gameId").asText();
                            Long dbGameId = gameExternalToInternalId.get(gameId);
                            setNullableLong(stmt, 2, dbGameId);

                            String dateStr = sessionNode.path("date").asText();
                            stmt.setTimestamp(3, parseTimestamp(dateStr));

                            stmt.setString(4, sessionNode.path("location").asText());

                            String organizerId = sessionNode.path("organizerId").asText();
                            Long dbOrganizerId = userExternalToInternalId.get(organizerId);
                            setNullableLong(stmt, 5, dbOrganizerId);

                            String winnerId = sessionNode.path("winnerId").asText();
                            Long dbWinnerId = userExternalToInternalId.get(winnerId);
                            setNullableLong(stmt, 6, dbWinnerId);

                            Integer duration = sessionNode.has("duration") && !sessionNode.path("duration").isNull()
                                    ? sessionNode.path("duration").asInt() : null;
                            setNullableInt(stmt, 7, duration);

                            stmt.setString(8, sessionNode.path("notes").asText());

                            stmt.executeUpdate();

                            long dbSessionId;
                            try (ResultSet gk = stmt.getGeneratedKeys()) {
                                if (gk.next()) {
                                    dbSessionId = gk.getLong(1);
                                } else {
                                    throw new SQLException("Failed to get generated key for session: " + sessionId);
                                }
                            }

                            // participantIds -> partida_participantes
                            JsonNode partsNode = sessionNode.path("participantIds");
                            if (partsNode.isArray()) {
                                try (PreparedStatement pStmt = conn.prepareStatement(
                                        "INSERT INTO partida_participantes (partida_id, usuario_id) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
                                    for (JsonNode pNode : partsNode) {
                                        String pUserId = pNode.asText();
                                        Long dbPUserId = userExternalToInternalId.get(pUserId);
                                        if (dbPUserId != null) {
                                            pStmt.setLong(1, dbSessionId);
                                            pStmt.setLong(2, dbPUserId);
                                            pStmt.executeUpdate();
                                        }
                                    }
                                }
                            }

                            // photos -> partida_fotos
                            JsonNode photosNode = sessionNode.path("photos");
                            if (photosNode.isArray()) {
                                try (PreparedStatement photoStmt = conn.prepareStatement(
                                        "INSERT INTO partida_fotos (partida_id, url, ordem) VALUES (?, ?, ?)")) {
                                    int order = 0;
                                    for (JsonNode photoNode : photosNode) {
                                        photoStmt.setLong(1, dbSessionId);
                                        photoStmt.setString(2, photoNode.asText());
                                        photoStmt.setInt(3, order++);
                                        photoStmt.executeUpdate();
                                    }
                                }
                            }

                            // comments -> comentarios
                            JsonNode commentsNode = sessionNode.path("comments");
                            if (commentsNode.isArray()) {
                                try (PreparedStatement commentStmt = conn.prepareStatement(
                                        "INSERT INTO comentarios (external_id, partida_id, usuario_id, conteudo, criado_em) VALUES (?, ?, ?, ?, ?)")) {
                                    for (JsonNode commentNode : commentsNode) {
                                        commentStmt.setString(1, commentNode.path("id").asText());
                                        commentStmt.setLong(2, dbSessionId);

                                        String commentUserId = commentNode.path("userId").asText();
                                        Long dbCommentUserId = userExternalToInternalId.get(commentUserId);
                                        setNullableLong(commentStmt, 3, dbCommentUserId);

                                        commentStmt.setString(4, commentNode.path("content").asText());

                                        String createdAtStr = commentNode.path("createdAt").asText();
                                        commentStmt.setTimestamp(5, parseTimestamp(createdAtStr));

                                        commentStmt.executeUpdate();
                                    }
                                }
                            }
                            sessionsSynced++;
                        }
                    }
                }

                // 6) Sync favoritos (User.favoriteGames -> favoritos)
                if (usersNode.isArray()) {
                    try (PreparedStatement favStmt = conn.prepareStatement(
                            "INSERT INTO favoritos (usuario_id, jogo_id) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
                        for (JsonNode userNode : usersNode) {
                            String userId = userNode.path("id").asText();
                            Long dbUserId = userExternalToInternalId.get(userId);
                            if (dbUserId == null) {
                                continue;
                            }

                            JsonNode favsNode = userNode.path("favoriteGames");
                            if (favsNode.isArray()) {
                                for (JsonNode favNode : favsNode) {
                                    String gameId = favNode.asText();
                                    Long dbGameId = gameExternalToInternalId.get(gameId);
                                    if (dbGameId != null) {
                                        favStmt.setLong(1, dbUserId);
                                        favStmt.setLong(2, dbGameId);
                                        favStmt.executeUpdate();
                                    }
                                }
                            }
                        }
                    }
                }

                // 7) Sync logs
                JsonNode logsNode = root.path("logs");
                if (logsNode.isArray()) {
                    String insLogSql = """
                            INSERT INTO logs (external_id, usuario_id, nome_usuario, acao, criado_em)
                            VALUES (?, ?, ?, ?, ?)
                            """;
                    try (PreparedStatement stmt = conn.prepareStatement(insLogSql)) {
                        for (JsonNode logNode : logsNode) {
                            stmt.setString(1, logNode.path("id").asText());

                            String logUserId = logNode.path("userId").asText();
                            Long dbLogUserId = userExternalToInternalId.get(logUserId);
                            setNullableLong(stmt, 2, dbLogUserId);

                            stmt.setString(3, logNode.path("userName").asText());
                            stmt.setString(4, logNode.path("action").asText());

                            String timestampStr = logNode.path("timestamp").asText();
                            stmt.setTimestamp(5, parseTimestamp(timestampStr));

                            stmt.executeUpdate();
                        }
                    }
                }

                conn.commit();
                LOGGER.atInfo()
                      .addKeyValue("state_id", 1)
                      .addKeyValue("operation", "relational_state_sync")
                      .addKeyValue("sync_result", "success")
                      .addKeyValue("users_synced", usersSynced)
                      .addKeyValue("games_synced", gamesSynced)
                      .addKeyValue("events_synced", eventsSynced)
                      .addKeyValue("sessions_synced", sessionsSynced)
                      .log("Relational state synchronization completed");
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    private static void setNullableInt(PreparedStatement stmt, int parameterIndex, Integer value) throws SQLException {
        if (value == null) {
            stmt.setNull(parameterIndex, Types.INTEGER);
        } else {
            stmt.setInt(parameterIndex, value);
        }
    }

    private static void setNullableLong(PreparedStatement stmt, int parameterIndex, Long value) throws SQLException {
        if (value == null) {
            stmt.setNull(parameterIndex, Types.BIGINT);
        } else {
            stmt.setLong(parameterIndex, value);
        }
    }

    private static void setNullableDouble(PreparedStatement stmt, int parameterIndex, Double value) throws SQLException {
        if (value == null) {
            stmt.setNull(parameterIndex, Types.NUMERIC);
        } else {
            stmt.setDouble(parameterIndex, value);
        }
    }

    public static java.sql.Timestamp parseTimestamp(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return new java.sql.Timestamp(System.currentTimeMillis());
        }
        try {
            return java.sql.Timestamp.from(java.time.Instant.parse(dateStr));
        } catch (Exception e) {
            try {
                return java.sql.Timestamp.from(java.time.OffsetDateTime.parse(dateStr).toInstant());
            } catch (Exception e2) {
                try {
                    return java.sql.Timestamp.valueOf(java.time.LocalDateTime.parse(dateStr));
                } catch (Exception e3) {
                    try {
                        return java.sql.Timestamp.valueOf(java.time.LocalDate.parse(dateStr).atStartOfDay());
                    } catch (Exception e4) {
                        return new java.sql.Timestamp(System.currentTimeMillis());
                    }
                }
            }
        }
    }
}

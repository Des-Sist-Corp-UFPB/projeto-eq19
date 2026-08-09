package br.com.tabula.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RelationalStateReadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelationalStateReadService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RelationalStateReadService() {
    }

    public static String readStateAsJson(HikariDataSource dataSource) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();

        ArrayNode usersArray = root.putArray("users");
        ArrayNode boardGamesArray = root.putArray("boardGames");
        ArrayNode sessionsArray = root.putArray("sessions");
        ArrayNode eventsArray = root.putArray("events");

        try (Connection conn = dataSource.getConnection()) {
            conn.setReadOnly(true);

            // 1. Users favorite games cache
            Map<String, List<String>> userFavorites = new HashMap<>();
            String favsSql = """
                    SELECT u.external_id AS user_ext, j.external_id AS game_ext
                    FROM favoritos f
                    JOIN usuarios u ON f.usuario_id = u.id
                    JOIN jogos j ON f.jogo_id = j.id
                    WHERE u.external_id IS NOT NULL AND j.external_id IS NOT NULL
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(favsSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String userExt = rs.getString("user_ext");
                    String gameExt = rs.getString("game_ext");
                    userFavorites.computeIfAbsent(userExt, k -> new ArrayList<>()).add(gameExt);
                }
            }

            // 2. Reconstruct users
            String usersSql = """
                    SELECT external_id, nome, email, role, avatar_url, bio, curso, criado_em
                    FROM usuarios
                    WHERE external_id IS NOT NULL
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(usersSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String externalId = rs.getString("external_id");
                    ObjectNode userNode = MAPPER.createObjectNode();
                    userNode.put("id", externalId);
                    userNode.put("name", rs.getString("nome"));
                    userNode.put("email", rs.getString("email"));
                    
                    String role = rs.getString("role");
                    userNode.put("role", "ADMIN".equalsIgnoreCase(role) ? "admin" : "student");
                    userNode.put("avatar", "🎲");
                    
                    String avatarUrl = rs.getString("avatar_url");
                    userNode.put("avatarUrl", avatarUrl);
                    
                    String course = rs.getString("curso");
                    userNode.put("course", course != null ? course : "");
                    
                    String bio = rs.getString("bio");
                    userNode.put("bio", bio != null ? bio : "");
                    
                    userNode.put("joinedAt", formatTimestamp(rs.getTimestamp("criado_em")));
                    userNode.put("winCount", 0);

                    ArrayNode favsArr = userNode.putArray("favoriteGames");
                    List<String> favs = userFavorites.get(externalId);
                    if (favs != null) {
                        for (String f : favs) {
                            favsArr.add(f);
                        }
                    }

                    usersArray.add(userNode);
                }
            }

            // 3. Reconstruct boardGames
            String gamesSql = """
                    SELECT external_id, nome, descricao, cover_url, categoria, min_players, max_players, avg_play_time, complexity
                    FROM jogos
                    WHERE external_id IS NOT NULL
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(gamesSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ObjectNode gameNode = MAPPER.createObjectNode();
                    gameNode.put("id", rs.getString("external_id"));
                    gameNode.put("name", rs.getString("nome"));
                    
                    String desc = rs.getString("descricao");
                    gameNode.put("description", desc != null ? desc : "");
                    
                    String cover = rs.getString("cover_url");
                    gameNode.put("coverUrl", cover != null && !cover.isBlank() ? cover : "/images/tabletop-placeholder.svg");
                    
                    String cat = rs.getString("categoria");
                    gameNode.put("category", cat != null && !cat.isBlank() ? cat : "Geral");
                    
                    int min = rs.getInt("min_players");
                    if (rs.wasNull() || min <= 0) {
                        min = 1;
                    }
                    gameNode.put("minPlayers", min);
                    
                    int max = rs.getInt("max_players");
                    if (rs.wasNull() || max <= 0) {
                        max = min;
                    }
                    gameNode.put("maxPlayers", max);
                    
                    int avg = rs.getInt("avg_play_time");
                    gameNode.put("avgPlayTime", rs.wasNull() ? 0 : avg);
                    
                    double comp = rs.getDouble("complexity");
                    gameNode.put("complexity", rs.wasNull() ? 1.0 : comp);

                    boardGamesArray.add(gameNode);
                }
            }

            // 4. Cache session participants
            Map<Long, List<String>> sessionParticipants = new HashMap<>();
            String sessionPartsSql = """
                    SELECT pp.partida_id, u.external_id AS user_ext
                    FROM partida_participantes pp
                    JOIN usuarios u ON pp.usuario_id = u.id
                    WHERE u.external_id IS NOT NULL
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(sessionPartsSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long partId = rs.getLong("partida_id");
                    String userExt = rs.getString("user_ext");
                    sessionParticipants.computeIfAbsent(partId, k -> new ArrayList<>()).add(userExt);
                }
            }

            // 5. Cache session photos
            Map<Long, List<String>> sessionPhotos = new HashMap<>();
            String sessionPhotosSql = """
                    SELECT pf.partida_id, pf.url
                    FROM partida_fotos pf
                    ORDER BY pf.partida_id, pf.ordem, pf.id
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(sessionPhotosSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long partId = rs.getLong("partida_id");
                    String url = rs.getString("url");
                    sessionPhotos.computeIfAbsent(partId, k -> new ArrayList<>()).add(url);
                }
            }

            // 6. Cache session comments
            Map<Long, List<ObjectNode>> sessionComments = new HashMap<>();
            String commentsSql = """
                    SELECT c.external_id, c.partida_id, u.external_id AS user_ext, u.nome AS user_name, c.conteudo, c.criado_em
                    FROM comentarios c
                    LEFT JOIN usuarios u ON c.usuario_id = u.id
                    ORDER BY c.partida_id, c.criado_em, c.id
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(commentsSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long partId = rs.getLong("partida_id");
                    
                    ObjectNode commentNode = MAPPER.createObjectNode();
                    commentNode.put("id", rs.getString("external_id"));
                    
                    String userExt = rs.getString("user_ext");
                    commentNode.put("userId", userExt != null ? userExt : "system");
                    
                    String userName = rs.getString("user_name");
                    commentNode.put("userName", userName != null ? userName : "Sistema");
                    commentNode.put("userAvatar", "🎲");
                    commentNode.put("content", rs.getString("conteudo"));
                    commentNode.put("createdAt", formatTimestamp(rs.getTimestamp("criado_em")));

                    sessionComments.computeIfAbsent(partId, k -> new ArrayList<>()).add(commentNode);
                }
            }

            // 7. Reconstruct sessions (partidas)
            String sessionsSql = """
                    SELECT p.id, p.external_id, j.external_id AS game_ext, p.data_hora, p.local, u_org.external_id AS org_ext, u_win.external_id AS win_ext, p.duracao_minutos, p.notas
                    FROM partidas p
                    JOIN jogos j ON p.jogo_id = j.id
                    LEFT JOIN usuarios u_org ON p.organizador_id = u_org.id
                    LEFT JOIN usuarios u_win ON p.vencedor_id = u_win.id
                    WHERE p.external_id IS NOT NULL AND j.external_id IS NOT NULL
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(sessionsSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long partId = rs.getLong("id");
                    ObjectNode sessionNode = MAPPER.createObjectNode();
                    sessionNode.put("id", rs.getString("external_id"));
                    sessionNode.put("gameId", rs.getString("game_ext"));
                    sessionNode.put("date", formatTimestamp(rs.getTimestamp("data_hora")));
                    
                    String loc = rs.getString("local");
                    sessionNode.put("location", loc != null ? loc : "");
                    
                    String orgExt = rs.getString("org_ext");
                    sessionNode.put("organizerId", orgExt != null ? orgExt : "system");
                    
                    String winExt = rs.getString("win_ext");
                    if (winExt == null) {
                        sessionNode.putNull("winnerId");
                    } else {
                        sessionNode.put("winnerId", winExt);
                    }
                    
                    int dur = rs.getInt("duracao_minutos");
                    sessionNode.put("duration", rs.wasNull() ? 0 : dur);
                    
                    String notes = rs.getString("notas");
                    sessionNode.put("notes", notes != null ? notes : "");

                    ArrayNode partsArr = sessionNode.putArray("participantIds");
                    List<String> participants = sessionParticipants.get(partId);
                    if (participants != null) {
                        for (String p : participants) {
                            partsArr.add(p);
                        }
                    }

                    ArrayNode photosArr = sessionNode.putArray("photos");
                    List<String> photos = sessionPhotos.get(partId);
                    if (photos != null) {
                        for (String p : photos) {
                            photosArr.add(p);
                        }
                    }

                    ArrayNode commentsArr = sessionNode.putArray("comments");
                    List<ObjectNode> comments = sessionComments.get(partId);
                    if (comments != null) {
                        for (ObjectNode c : comments) {
                            commentsArr.add(c);
                        }
                    }

                    sessionsArray.add(sessionNode);
                }
            }

            // 8. Cache event participants
            Map<Long, List<String>> eventParticipants = new HashMap<>();
            Map<Long, List<String>> eventWaitingList = new HashMap<>();
            String eventPartsSql = """
                    SELECT ep.evento_id, u.external_id AS user_ext, ep.tipo
                    FROM evento_participantes ep
                    JOIN usuarios u ON ep.usuario_id = u.id
                    WHERE u.external_id IS NOT NULL
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(eventPartsSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long evtId = rs.getLong("evento_id");
                    String userExt = rs.getString("user_ext");
                    String type = rs.getString("tipo");

                    if ("PARTICIPANT".equalsIgnoreCase(type)) {
                        eventParticipants.computeIfAbsent(evtId, k -> new ArrayList<>()).add(userExt);
                    } else if ("WAITING".equalsIgnoreCase(type)) {
                        eventWaitingList.computeIfAbsent(evtId, k -> new ArrayList<>()).add(userExt);
                    }
                }
            }

            // 9. Reconstruct events
            String eventsSql = """
                    SELECT e.id, e.external_id, j.external_id AS game_ext, e.data_hora, e.local, e.descricao, e.max_participantes, e.status, u_org.external_id AS org_ext
                    FROM eventos e
                    JOIN jogos j ON e.jogo_id = j.id
                    LEFT JOIN usuarios u_org ON e.organizador_id = u_org.id
                    WHERE e.external_id IS NOT NULL AND j.external_id IS NOT NULL
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(eventsSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long evtId = rs.getLong("id");
                    ObjectNode eventNode = MAPPER.createObjectNode();
                    eventNode.put("id", rs.getString("external_id"));
                    eventNode.put("gameId", rs.getString("game_ext"));
                    
                    Timestamp ts = rs.getTimestamp("data_hora");
                    eventNode.put("date", formatDate(ts));
                    eventNode.put("time", formatTime(ts));
                    
                    String loc = rs.getString("local");
                    eventNode.put("location", loc != null ? loc : "");
                    
                    int maxParts = rs.getInt("max_participantes");
                    eventNode.put("maxParticipants", rs.wasNull() ? 0 : maxParts);
                    
                    String desc = rs.getString("descricao");
                    eventNode.put("description", desc != null ? desc : "");
                    
                    String orgExt = rs.getString("org_ext");
                    eventNode.put("organizerId", orgExt != null ? orgExt : "system");
                    
                    String status = rs.getString("status");
                    eventNode.put("status", status != null && !status.isBlank() ? status : "active");

                    ArrayNode partsArr = eventNode.putArray("participantIds");
                    List<String> participants = eventParticipants.get(evtId);
                    if (participants != null) {
                        for (String p : participants) {
                            partsArr.add(p);
                        }
                    }

                    ArrayNode waitArr = eventNode.putArray("waitingListIds");
                    List<String> waitList = eventWaitingList.get(evtId);
                    if (waitList != null) {
                        for (String w : waitList) {
                            waitArr.add(w);
                        }
                    }

                    eventsArray.add(eventNode);
                }
            }

        }

        if (!root.has("users") || !root.has("boardGames") || !root.has("sessions") || !root.has("events")) {
            throw new IllegalStateException("Reconstructed JSON is missing one or more required top-level arrays.");
        }

        return MAPPER.writeValueAsString(root);
    }

    private static String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant().toString();
    }

    private static String formatDate(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.toLocalDateTime().toLocalDate().toString();
    }

    private static String formatTime(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        java.time.LocalTime time = timestamp.toLocalDateTime().toLocalTime();
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }
}

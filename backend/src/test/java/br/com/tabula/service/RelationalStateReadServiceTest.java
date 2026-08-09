package br.com.tabula.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelationalStateReadServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldReadEmptyRelationalTablesSuccessfully() throws Exception {
        HikariDataSource ds = mock(HikariDataSource.class);
        Connection conn = mock(Connection.class);

        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenAnswer(invocation -> {
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);
            return stmt;
        });

        String json = RelationalStateReadService.readStateAsJson(ds);
        assertNotNull(json);

        JsonNode root = MAPPER.readTree(json);
        assertTrue(root.has("users") && root.get("users").isArray() && root.get("users").isEmpty());
        assertTrue(root.has("boardGames") && root.get("boardGames").isArray() && root.get("boardGames").isEmpty());
        assertTrue(root.has("sessions") && root.get("sessions").isArray() && root.get("sessions").isEmpty());
        assertTrue(root.has("events") && root.get("events").isArray() && root.get("events").isEmpty());
        assertTrue(!root.has("logs"));
    }

    @Test
    void shouldReadFilledRelationalTablesSuccessfully() throws Exception {
        HikariDataSource ds = mock(HikariDataSource.class);
        Connection conn = mock(Connection.class);

        when(ds.getConnection()).thenReturn(conn);

        Timestamp mockTime = Timestamp.valueOf("2026-07-09 20:00:00");

        when(conn.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            String norm = sql.replaceAll("\\s+", " ").trim();
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(stmt.executeQuery()).thenReturn(rs);

            if (norm.contains("FROM favoritos")) {
                // favorites
                when(rs.next()).thenReturn(true, false);
                when(rs.getString("user_ext")).thenReturn("u_1");
                when(rs.getString("game_ext")).thenReturn("g_1");
            } else if (norm.contains("FROM partida_participantes")) {
                // partida_participantes
                when(rs.next()).thenReturn(true, false);
                when(rs.getLong("partida_id")).thenReturn(401L);
                when(rs.getString("user_ext")).thenReturn("u_1");
            } else if (norm.contains("FROM partida_fotos")) {
                // photos
                when(rs.next()).thenReturn(true, false);
                when(rs.getLong("partida_id")).thenReturn(401L);
                when(rs.getString("url")).thenReturn("photo1");
            } else if (norm.contains("FROM comentarios")) {
                // comments
                when(rs.next()).thenReturn(true, false);
                when(rs.getLong("partida_id")).thenReturn(401L);
                when(rs.getString("external_id")).thenReturn("c_1");
                when(rs.getString("user_ext")).thenReturn("u_1");
                when(rs.getString("user_name")).thenReturn("User One");
                when(rs.getString("conteudo")).thenReturn("comment1");
                when(rs.getTimestamp("criado_em")).thenReturn(mockTime);
            } else if (norm.contains("FROM evento_participantes")) {
                // event participants
                when(rs.next()).thenReturn(true, true, false);
                when(rs.getLong("evento_id")).thenReturn(301L);
                when(rs.getString("user_ext")).thenReturn("u_1");
                when(rs.getString("tipo")).thenReturn("PARTICIPANT", "WAITING");
            } else if (norm.contains("FROM eventos")) {
                // events
                when(rs.next()).thenReturn(true, false);
                when(rs.getLong("id")).thenReturn(301L);
                when(rs.getString("external_id")).thenReturn("e_1");
                when(rs.getString("game_ext")).thenReturn("g_1");
                when(rs.getTimestamp("data_hora")).thenReturn(mockTime);
                when(rs.getString("local")).thenReturn("Loc 2");
                when(rs.getInt("max_participantes")).thenReturn(8);
                when(rs.getString("descricao")).thenReturn("desc2");
                when(rs.getString("org_ext")).thenReturn("u_1");
                when(rs.getString("status")).thenReturn("active");
            } else if (norm.contains("FROM partidas")) {
                // sessions
                when(rs.next()).thenReturn(true, false);
                when(rs.getLong("id")).thenReturn(401L);
                when(rs.getString("external_id")).thenReturn("s_1");
                when(rs.getString("game_ext")).thenReturn("g_1");
                when(rs.getTimestamp("data_hora")).thenReturn(mockTime);
                when(rs.getString("local")).thenReturn("Loc 1");
                when(rs.getString("org_ext")).thenReturn("u_1");
                when(rs.getString("win_ext")).thenReturn("u_1");
                when(rs.getInt("duracao_minutos")).thenReturn(60);
                when(rs.getString("notas")).thenReturn("notes1");
            } else if (norm.contains("FROM usuarios")) {
                // users
                when(rs.next()).thenReturn(true, false);
                when(rs.getString("external_id")).thenReturn("u_1");
                when(rs.getString("nome")).thenReturn("User One");
                when(rs.getString("email")).thenReturn("user1@example.com");
                when(rs.getString("role")).thenReturn("ADMIN");
                when(rs.getString("avatar_url")).thenReturn("url1");
                when(rs.getString("bio")).thenReturn("bio1");
                when(rs.getString("curso")).thenReturn("course1");
                when(rs.getTimestamp("criado_em")).thenReturn(mockTime);
            } else if (norm.contains("FROM jogos")) {
                // games
                when(rs.next()).thenReturn(true, false);
                when(rs.getString("external_id")).thenReturn("g_1");
                when(rs.getString("nome")).thenReturn("Game One");
                when(rs.getString("descricao")).thenReturn("desc1");
                when(rs.getString("cover_url")).thenReturn("");
                when(rs.getString("categoria")).thenReturn("");
                when(rs.getInt("min_players")).thenReturn(2);
                when(rs.getInt("max_players")).thenReturn(4);
                when(rs.getInt("avg_play_time")).thenReturn(45);
                when(rs.getDouble("complexity")).thenReturn(2.5);
            } else {
                when(rs.next()).thenReturn(false);
            }

            return stmt;
        });

        String json = RelationalStateReadService.readStateAsJson(ds);
        assertNotNull(json);

        JsonNode root = MAPPER.readTree(json);
        
        assertEquals(1, root.get("users").size(), root.toPrettyString());
        assertEquals(1, root.get("boardGames").size(), root.toPrettyString());
        assertEquals(1, root.get("sessions").size(), root.toPrettyString());
        assertEquals(1, root.get("events").size(), root.toPrettyString());
        assertTrue(!root.has("logs"));

        assertEquals("u_1", root.get("users").get(0).get("id").asText());
        assertEquals("admin", root.get("users").get(0).get("role").asText());
        assertEquals("g_1", root.get("users").get(0).get("favoriteGames").get(0).asText());

        assertEquals("g_1", root.get("boardGames").get(0).get("id").asText());
        assertEquals("/images/tabletop-placeholder.svg", root.get("boardGames").get(0).get("coverUrl").asText());

        assertEquals("s_1", root.get("sessions").get(0).get("id").asText());
        assertEquals("photo1", root.get("sessions").get(0).get("photos").get(0).asText());
        assertEquals("c_1", root.get("sessions").get(0).get("comments").get(0).get("id").asText());

        assertEquals("e_1", root.get("events").get(0).get("id").asText());
        assertEquals("2026-07-09", root.get("events").get(0).get("date").asText());
        assertEquals("20:00", root.get("events").get(0).get("time").asText());
        assertEquals("u_1", root.get("events").get(0).get("participantIds").get(0).asText());
        assertEquals("u_1", root.get("events").get(0).get("waitingListIds").get(0).asText());
    }

    @Test
    void shouldMockPrivateConstructor() throws Exception {
        java.lang.reflect.Constructor<RelationalStateReadService> constructor = RelationalStateReadService.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        RelationalStateReadService instance = constructor.newInstance();
        assertNotNull(instance);
    }
}

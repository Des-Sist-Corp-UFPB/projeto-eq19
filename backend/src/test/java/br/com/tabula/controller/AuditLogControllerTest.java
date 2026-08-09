package br.com.tabula.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogControllerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldRequireAuthenticationWithoutQueryingTheDatabase() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = start(dataSource);
        try {
            HttpResponse<String> response = send(app, "/audit-logs", null);

            assertEquals(401, response.statusCode());
            verify(dataSource, never()).getConnection();
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectNonAdminUsers() throws Exception {
        HikariDataSource dataSource = dataSourceForRole("USER", false, new ArrayList<>());
        Javalin app = start(dataSource);
        try {
            HttpResponse<String> response = send(app, "/audit-logs", "Bearer valid-user");

            assertEquals(403, response.statusCode());
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnFilteredReadOnlyPageAndClampMaximumPageSize() throws Exception {
        List<String> preparedSql = new ArrayList<>();
        HikariDataSource dataSource = dataSourceForRole("ADMIN", true, preparedSql);
        Javalin app = start(dataSource);
        try {
            String path = "/audit-logs?page=2&pageSize=999&action=PROFILE_UPDATED"
                    + "&userId=u_7&resourceType=PROFILE&resourceId=u_7&success=true"
                    + "&startDate=2026-01-01T00:00:00Z&endDate=2026-12-31T23:59:59Z";
            HttpResponse<String> response = send(app, path, "Bearer admin-token");

            assertEquals(200, response.statusCode(), response.body());
            JsonNode body = MAPPER.readTree(response.body());
            assertEquals(2, body.get("page").asInt());
            assertEquals(100, body.get("pageSize").asInt());
            assertEquals(1, body.get("total").asLong());
            assertEquals("PROFILE_UPDATED", body.get("items").get(0).get("action").asText());
            assertEquals("u_7", body.get("items").get(0).get("userId").asText());
            assertEquals("Ana Admin", body.get("items").get(0).get("actorName").asText());
            assertEquals("ana@example.com", body.get("items").get(0).get("actorEmail").asText());
            assertTrue(preparedSql.stream().anyMatch(sql ->
                    sql.contains("ORDER BY criado_em DESC, id DESC LIMIT ? OFFSET ?")));
            assertTrue(preparedSql.stream().allMatch(sql ->
                    sql.stripLeading().toUpperCase().startsWith("SELECT")));
            assertTrue(preparedSql.stream().noneMatch(sql ->
                    sql.matches("(?is).*\\b(UPDATE|DELETE|TRUNCATE|INSERT)\\b.*")));
        } finally {
            app.stop();
        }
    }

    private static HikariDataSource dataSourceForRole(
            String role,
            boolean includeAuditRows,
            List<String> preparedSql) throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement authStatement = mock(PreparedStatement.class);
        PreparedStatement pageStatement = mock(PreparedStatement.class);
        PreparedStatement countStatement = mock(PreparedStatement.class);
        ResultSet authResult = mock(ResultSet.class);
        ResultSet pageResult = mock(ResultSet.class);
        ResultSet countResult = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            preparedSql.add(sql);
            if (sql.contains("FROM auth_tokens")) return authStatement;
            if (sql.contains("COUNT(*)")) return countStatement;
            if (sql.contains("FROM audit_logs")) return pageStatement;
            throw new AssertionError("Unexpected SQL: " + sql);
        });

        when(authStatement.executeQuery()).thenReturn(authResult);
        when(authResult.next()).thenReturn(true, false);
        when(authResult.getLong("id")).thenReturn(7L);
        when(authResult.getString("external_id")).thenReturn("u_7");
        when(authResult.getString("role")).thenReturn(role);

        when(pageStatement.executeQuery()).thenReturn(pageResult);
        when(pageResult.next()).thenReturn(includeAuditRows, false);
        when(pageResult.getLong("id")).thenReturn(99L);
        when(pageResult.getLong("usuario_id")).thenReturn(7L);
        when(pageResult.wasNull()).thenReturn(false);
        when(pageResult.getString("ator_id_externo")).thenReturn("u_7");
        when(pageResult.getString("acao")).thenReturn("PROFILE_UPDATED");
        when(pageResult.getString("tipo_recurso")).thenReturn("PROFILE");
        when(pageResult.getString("recurso_id")).thenReturn("u_7");
        when(pageResult.getString("detalhes")).thenReturn("{\"changedSections\":[\"users\"]}");
        when(pageResult.getString("endereco_ip")).thenReturn("127.0.0.1");
        when(pageResult.getString("user_agent")).thenReturn("agent");
        when(pageResult.getString("actor_name")).thenReturn("Ana Admin");
        when(pageResult.getString("actor_email")).thenReturn("ana@example.com");
        when(pageResult.getBoolean("sucesso")).thenReturn(true);
        when(pageResult.getString("trace_id")).thenReturn("0123456789abcdef0123456789abcdef");
        when(pageResult.getTimestamp("criado_em"))
                .thenReturn(Timestamp.from(Instant.parse("2026-07-29T12:00:00Z")));

        when(countStatement.executeQuery()).thenReturn(countResult);
        when(countResult.next()).thenReturn(true, false);
        when(countResult.getLong(1)).thenReturn(includeAuditRows ? 1L : 0L);
        return dataSource;
    }

    private static Javalin start(HikariDataSource dataSource) {
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        AuditLogController.register(app, dataSource);
        app.start(0);
        return app;
    }

    private static HttpResponse<String> send(Javalin app, String path, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .GET();
        if (authorization != null) request.header("Authorization", authorization);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}

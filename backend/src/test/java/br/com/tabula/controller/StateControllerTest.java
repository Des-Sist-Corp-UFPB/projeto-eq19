package br.com.tabula.controller;

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
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StateControllerTest {

    @Test
    void shouldReturnNotFoundWhenStateIsMissing() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/state");
            assertEquals(404, response.statusCode());
            assertTrue(response.body().contains("Estado ainda não inicializado"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnStoredStatePayload() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("{\"ready\":true}");

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/state");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"ready\":true"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectPutStateWithoutPayload() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = startStateApp(dataSource);

        try {
            HttpResponse<String> response = sendPut(app, "/state", "");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("Payload vazio"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectPutStateWithoutValidBearerTokenWhenStateExists() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        PreparedStatement tokenStatement = mock(PreparedStatement.class);
        ResultSet existsResultSet = mock(ResultSet.class);
        ResultSet tokenResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(existsStatement, tokenStatement);
        when(existsStatement.executeQuery()).thenReturn(existsResultSet);
        when(existsResultSet.next()).thenReturn(true);
        when(tokenStatement.executeQuery()).thenReturn(tokenResultSet);
        when(tokenResultSet.next()).thenReturn(false);

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendPut(app, "/state", "{\"value\":1}", null);
            assertEquals(401, response.statusCode());
            assertTrue(response.body().contains("Sessão inválida ou expirada"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldStoreStateWhenPayloadIsValid() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        PreparedStatement tokenStatement = mock(PreparedStatement.class);
        PreparedStatement saveStatement = mock(PreparedStatement.class);
        ResultSet existsResultSet = mock(ResultSet.class);
        ResultSet tokenResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(existsStatement, tokenStatement, saveStatement);
        when(existsStatement.executeQuery()).thenReturn(existsResultSet);
        when(existsResultSet.next()).thenReturn(true);
        when(tokenStatement.executeQuery()).thenReturn(tokenResultSet);
        when(tokenResultSet.next()).thenReturn(true);
        when(saveStatement.executeUpdate()).thenReturn(1);

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendPut(app, "/state", "{\"value\":1}", "Bearer abc");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"ok\":true"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnServerErrorWhenReadingStateFails() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(new SQLException("boom"));

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/state");
            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("Não foi possível carregar os dados"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldSaveStateWhenStateDoesNotExist() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        PreparedStatement saveStatement = mock(PreparedStatement.class);
        ResultSet existsResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(existsStatement, saveStatement);
        when(existsStatement.executeQuery()).thenReturn(existsResultSet);
        when(existsResultSet.next()).thenReturn(false);
        when(saveStatement.executeUpdate()).thenReturn(1);

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendPut(app, "/state", "{\"value\":1}");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"ok\":true"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldCoverPrivateConstructor() throws Exception {
        java.lang.reflect.Constructor<StateController> constructor = StateController.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        StateController instance = constructor.newInstance();
        assertTrue(instance != null);
    }

    @Test
    void shouldReturnServerErrorWhenSavingStateFails() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(new java.sql.SQLException("boom"));

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendPut(app, "/state", "{\"value\":1}");
            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("Não foi possível salvar os dados"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectPutStateWithInvalidTokenFormats() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        ResultSet existsResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(existsResultSet);
        when(existsResultSet.next()).thenReturn(true);

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response1 = sendPut(app, "/state", "{\"value\":1}", "Basic xyz");
            assertEquals(401, response1.statusCode());
            assertTrue(response1.body().contains("Sessão inválida ou expirada"));

            HttpResponse<String> response2 = sendPut(app, "/state", "{\"value\":1}", "Bearer  ");
            assertEquals(401, response2.statusCode());
            assertTrue(response2.body().contains("Sessão inválida ou expirada"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldNotFailPutStateWhenRelationalSyncThrowsException() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        
        Connection connection1 = mock(Connection.class);
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        ResultSet rs1 = mock(ResultSet.class);
        when(connection1.prepareStatement(anyString())).thenReturn(stmt1);
        when(stmt1.executeQuery()).thenReturn(rs1);
        when(rs1.next()).thenReturn(true);

        Connection connection2 = mock(Connection.class);
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        ResultSet rs2 = mock(ResultSet.class);
        when(connection2.prepareStatement(anyString())).thenReturn(stmt2);
        when(stmt2.executeQuery()).thenReturn(rs2);
        when(rs2.next()).thenReturn(true);

        Connection connection3 = mock(Connection.class);
        PreparedStatement stmt3 = mock(PreparedStatement.class);
        when(connection3.prepareStatement(anyString())).thenReturn(stmt3);
        when(stmt3.executeUpdate()).thenReturn(1);

        when(dataSource.getConnection())
                .thenReturn(connection1)
                .thenReturn(connection2)
                .thenReturn(connection3)
                .thenThrow(new SQLException("database down during sync"));

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendPut(app, "/state", "{\"value\":1}", "Bearer abc");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"ok\":true"));
        } finally {
            app.stop();
        }
    }

    @org.junit.jupiter.api.AfterEach
    void clearRelationalFlags() {
        System.clearProperty("RELATIONAL_STATE_READ_ENABLED");
        System.clearProperty("RELATIONAL_STATE_READ_GUARD_ENABLED");
        System.clearProperty("RELATIONAL_STATE_COMPARISON_ENABLED");
        System.clearProperty("RELATIONAL_STATE_BACKFILL_ENABLED");
    }

    @Test
    void shouldReturnRelationalJsonWhenGuardIsDisabled() throws Exception {
        System.setProperty("RELATIONAL_STATE_READ_ENABLED", "true");
        System.clearProperty("RELATIONAL_STATE_READ_GUARD_ENABLED");

        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement appStateStatement = mock(PreparedStatement.class);
        ResultSet appStateResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);

        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(stmt.executeQuery()).thenReturn(rs);

            if (sql.contains("app_state")) {
                return appStateStatement;
            }

            if (sql.contains("usuarios")) {
                when(rs.next()).thenReturn(true, false);
                when(rs.getString("external_id")).thenReturn("relational_u");
            } else {
                when(rs.next()).thenReturn(false);
            }
            return stmt;
        });

        when(appStateStatement.executeQuery()).thenReturn(appStateResultSet);
        when(appStateResultSet.next()).thenReturn(true);
        when(appStateResultSet.getString(1)).thenReturn("{\"users\":[{\"id\":\"legacy_u\"}],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}");

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/state");
            assertEquals(200, response.statusCode());

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            assertEquals("relational_u", root.get("users").get(0).get("id").asText());
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnRelationalJsonWhenGuardIsEnabledAndComparisonPasses() throws Exception {
        System.setProperty("RELATIONAL_STATE_READ_ENABLED", "true");
        System.setProperty("RELATIONAL_STATE_READ_GUARD_ENABLED", "true");
        
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement appStateStatement = mock(PreparedStatement.class);
        ResultSet appStateResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);

            if (sql.contains("app_state")) {
                return appStateStatement;
            }
            return stmt;
        });

        when(appStateStatement.executeQuery()).thenReturn(appStateResultSet);
        when(appStateResultSet.next()).thenReturn(true);
        when(appStateResultSet.getString(1)).thenReturn("{\"users\":[],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}");

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/state");
            assertEquals(200, response.statusCode());

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            assertTrue(root.get("users").isArray());
            assertEquals(0, root.get("users").size());
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnLegacyJsonWhenGuardIsEnabledAndComparisonFails() throws Exception {
        System.setProperty("RELATIONAL_STATE_READ_ENABLED", "true");
        System.setProperty("RELATIONAL_STATE_READ_GUARD_ENABLED", "true");
        
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement appStateStatement = mock(PreparedStatement.class);
        ResultSet appStateResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);

            if (sql.contains("app_state")) {
                return appStateStatement;
            }
            return stmt;
        });

        when(appStateStatement.executeQuery()).thenReturn(appStateResultSet);
        when(appStateResultSet.next()).thenReturn(true);
        when(appStateResultSet.getString(1)).thenReturn("{\"users\":[{\"id\":\"u1\",\"name\":\"User\",\"email\":\"u@test.com\",\"role\":\"student\"}],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}");

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/state");
            assertEquals(200, response.statusCode());

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            assertTrue(root.get("users").isArray());
            assertEquals(1, root.get("users").size());
            assertEquals("u1", root.get("users").get(0).get("id").asText());
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnLegacyJsonWhenGuardIsEnabledAndRelationalReadThrows() throws Exception {
        System.setProperty("RELATIONAL_STATE_READ_ENABLED", "true");
        System.setProperty("RELATIONAL_STATE_READ_GUARD_ENABLED", "true");
        
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement appStateStatement = mock(PreparedStatement.class);
        ResultSet appStateResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("app_state")) {
                return appStateStatement;
            } else {
                throw new SQLException("relational database down during read");
            }
        });

        when(appStateStatement.executeQuery()).thenReturn(appStateResultSet);
        when(appStateResultSet.next()).thenReturn(true);
        when(appStateResultSet.getString(1)).thenReturn("{\"users\":[{\"id\":\"legacy_user\"}],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}");

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/state");
            assertEquals(200, response.statusCode());

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            assertEquals("legacy_user", root.get("users").get(0).get("id").asText());
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnLegacyJsonWhenGuardIsEnabledAndComparisonThrows() throws Exception {
        System.setProperty("RELATIONAL_STATE_READ_ENABLED", "true");
        System.setProperty("RELATIONAL_STATE_READ_GUARD_ENABLED", "true");
        
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement appStateStatement = mock(PreparedStatement.class);
        ResultSet appStateResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("app_state")) {
                return appStateStatement;
            } else {
                return null;
            }
        });

        when(appStateStatement.executeQuery()).thenReturn(appStateResultSet);
        when(appStateResultSet.next()).thenReturn(true);
        when(appStateResultSet.getString(1)).thenReturn("{\"users\":[{\"id\":\"legacy_user_on_err\"}],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}");

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/state");
            assertEquals(200, response.statusCode());

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            assertEquals("legacy_user_on_err", root.get("users").get(0).get("id").asText());
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturn404WhenBackfillIsDisabled() throws Exception {
        System.setProperty("RELATIONAL_STATE_BACKFILL_ENABLED", "false");
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = startStateApp(dataSource);
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + app.port() + "/state/relational-backfill"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnOkTrueWhenBackfillSucceedsAndValidationPasses() throws Exception {
        System.setProperty("RELATIONAL_STATE_BACKFILL_ENABLED", "true");
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement appStateStatement = mock(PreparedStatement.class);
        ResultSet appStateResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);

        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);

            if (sql.contains("app_state")) {
                return appStateStatement;
            }
            return stmt;
        });

        when(appStateStatement.executeQuery()).thenReturn(appStateResultSet);
        when(appStateResultSet.next()).thenReturn(true);
        when(appStateResultSet.getString(1)).thenReturn("{\"users\":[],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}");

        Javalin app = startStateApp(dataSource);
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + app.port() + "/state/relational-backfill"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            assertTrue(root.get("ok").asBoolean(), root.toPrettyString());
            assertEquals("Relational backfill completed", root.get("message").asText());
            assertTrue(root.get("comparison").isObject());
            assertTrue(root.get("comparison").get("ok").asBoolean());
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnOkFalseWhenBackfillSucceedsButValidationFails() throws Exception {
        System.setProperty("RELATIONAL_STATE_BACKFILL_ENABLED", "true");
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement appStateStatement = mock(PreparedStatement.class);
        ResultSet appStateResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);

        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);

            if (sql.contains("app_state")) {
                return appStateStatement;
            }
            return stmt;
        });

        when(appStateStatement.executeQuery()).thenReturn(appStateResultSet);
        when(appStateResultSet.next()).thenReturn(true);
        when(appStateResultSet.getString(1)).thenReturn("{\"users\":[{\"id\":\"u1\",\"name\":\"A\",\"email\":\"a@b.com\",\"role\":\"student\"}],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}");

        Javalin app = startStateApp(dataSource);
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + app.port() + "/state/relational-backfill"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            assertFalse(root.get("ok").asBoolean(), root.toPrettyString());
            assertEquals("Relational backfill completed with validation errors", root.get("message").asText());
            assertTrue(root.get("comparison").isObject());
            assertFalse(root.get("comparison").get("ok").asBoolean());
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnOkFalseAnd500WhenBackfillThrowsException() throws Exception {
        System.setProperty("RELATIONAL_STATE_BACKFILL_ENABLED", "true");
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement appStateStatement = mock(PreparedStatement.class);
        ResultSet appStateResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);

        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("app_state")) {
                return appStateStatement;
            } else {
                throw new SQLException("relational database sync error during backfill");
            }
        });

        when(appStateStatement.executeQuery()).thenReturn(appStateResultSet);
        when(appStateResultSet.next()).thenReturn(true);
        when(appStateResultSet.getString(1)).thenReturn("{\"users\":[],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}");

        Javalin app = startStateApp(dataSource);
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + app.port() + "/state/relational-backfill"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(500, response.statusCode());

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            assertFalse(root.get("ok").asBoolean(), root.toPrettyString());
            assertEquals("Relational backfill failed", root.get("message").asText());
            assertTrue(root.get("errors").isArray());
            assertTrue(root.get("errors").get(0).asText().contains("relational database sync error during backfill"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturn404WhenComparisonIsDisabled() throws Exception {
        System.setProperty("RELATIONAL_STATE_COMPARISON_ENABLED", "false");
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/state/relational-comparison");
            assertEquals(404, response.statusCode());
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnComparisonReportWhenComparisonIsEnabled() throws Exception {
        System.setProperty("RELATIONAL_STATE_COMPARISON_ENABLED", "true");
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement appStateStatement = mock(PreparedStatement.class);
        ResultSet appStateResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);

        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);

            if (sql.contains("app_state")) {
                return appStateStatement;
            }
            return stmt;
        });

        when(appStateStatement.executeQuery()).thenReturn(appStateResultSet);
        when(appStateResultSet.next()).thenReturn(true);
        when(appStateResultSet.getString(1)).thenReturn("{\"users\":[],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}");

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/state/relational-comparison");
            assertEquals(200, response.statusCode());
            
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            assertTrue(root.get("ok").asBoolean(), root.toPrettyString());
            assertTrue(root.get("errors").isArray());
            assertEquals(0, root.get("errors").size(), root.toPrettyString());
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnOkFalseReportWhenRelationalReadFails() throws Exception {
        System.setProperty("RELATIONAL_STATE_COMPARISON_ENABLED", "true");
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement appStateStatement = mock(PreparedStatement.class);
        ResultSet appStateResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);

        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("app_state")) {
                return appStateStatement;
            } else {
                throw new SQLException("relational database down");
            }
        });

        when(appStateStatement.executeQuery()).thenReturn(appStateResultSet);
        when(appStateResultSet.next()).thenReturn(true);
        when(appStateResultSet.getString(1)).thenReturn("{\"users\":[],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}");

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/state/relational-comparison");
            assertEquals(200, response.statusCode());
            
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            org.junit.jupiter.api.Assertions.assertFalse(root.get("ok").asBoolean());
            assertTrue(root.get("errors").isArray());
            assertTrue(root.get("errors").size() > 0, root.toPrettyString());
            
            String body = response.body();
            assertTrue(body.contains("relational") || body.contains("database"), body);
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReadFromAppStateWhenRelationalReadIsDisabled() throws Exception {
        System.setProperty("RELATIONAL_STATE_READ_ENABLED", "false");
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("{\"ready\":true}");

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/state");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"ready\":true"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReadFromRelationalReconstructionWhenEnabled() throws Exception {
        System.setProperty("RELATIONAL_STATE_READ_ENABLED", "true");
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);
            return stmt;
        });

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/state");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"users\":[]"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldFallbackToAppStateWhenRelationalReadThrowsException() throws Exception {
        System.setProperty("RELATIONAL_STATE_READ_ENABLED", "true");
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement appStateStatement = mock(PreparedStatement.class);
        ResultSet appStateResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("app_state")) {
                return appStateStatement;
            } else {
                throw new SQLException("relational schema not ready");
            }
        });
        when(appStateStatement.executeQuery()).thenReturn(appStateResultSet);
        when(appStateResultSet.next()).thenReturn(true);
        when(appStateResultSet.getString(1)).thenReturn("{\"fallback\":true}");

        Javalin app = startStateApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/state");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"fallback\":true"));
        } finally {
            app.stop();
        }
    }

    private static Javalin startStateApp(HikariDataSource dataSource) {
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        StateController.register(app, dataSource);
        app.start(0);
        return app;
    }

    private static HttpResponse<String> sendGet(Javalin app, String path) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> sendPut(Javalin app, String path, String body) throws Exception {
        return sendPut(app, path, body, null);
    }

    private static HttpResponse<String> sendPut(Javalin app, String path, String body, String authorization) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body));
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}

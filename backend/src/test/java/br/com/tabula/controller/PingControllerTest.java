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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PingControllerTest {

    private HttpResponse<String> sendGetRequest(Javalin app, String endpoint) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + endpoint))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void shouldReturn200OnPingWhenDatabaseIsHealthy() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);

        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        PingController.register(app, dataSource);
        app.start(0);

        try {
            HttpResponse<String> responsePing = sendGetRequest(app, "/ping");
            assertEquals(200, responsePing.statusCode());
            assertTrue(responsePing.body().contains("\"status\":\"ok\""));
            assertTrue(responsePing.body().contains("\"database\":\"up\""));

            HttpResponse<String> responseApiPing = sendGetRequest(app, "/api/ping");
            assertEquals(200, responseApiPing.statusCode());
            assertTrue(responseApiPing.body().contains("\"status\":\"ok\""));
            assertTrue(responseApiPing.body().contains("\"database\":\"up\""));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturn503OnPingWhenDatabaseThrowsException() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection timeout"));

        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        PingController.register(app, dataSource);
        app.start(0);

        try {
            HttpResponse<String> responsePing = sendGetRequest(app, "/ping");
            assertEquals(503, responsePing.statusCode());
            assertTrue(responsePing.body().contains("\"status\":\"error\""));
            assertTrue(responsePing.body().contains("\"database\":\"down\""));
            assertTrue(responsePing.body().contains("\"error\":\"Database unavailable\""));

            HttpResponse<String> responseApiPing = sendGetRequest(app, "/api/ping");
            assertEquals(503, responseApiPing.statusCode());
            assertTrue(responseApiPing.body().contains("\"status\":\"error\""));
            assertTrue(responseApiPing.body().contains("\"database\":\"down\""));
            assertTrue(responseApiPing.body().contains("\"error\":\"Database unavailable\""));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturn503OnPingWhenDataSourceIsNull() throws Exception {
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        PingController.register(app, null);
        app.start(0);

        try {
            HttpResponse<String> responsePing = sendGetRequest(app, "/ping");
            assertEquals(503, responsePing.statusCode());
            assertTrue(responsePing.body().contains("\"status\":\"error\""));
            assertTrue(responsePing.body().contains("\"database\":\"down\""));
            assertTrue(responsePing.body().contains("\"error\":\"Database unavailable\""));

            HttpResponse<String> responseApiPing = sendGetRequest(app, "/api/ping");
            assertEquals(503, responseApiPing.statusCode());
            assertTrue(responseApiPing.body().contains("\"status\":\"error\""));
            assertTrue(responseApiPing.body().contains("\"database\":\"down\""));
            assertTrue(responseApiPing.body().contains("\"error\":\"Database unavailable\""));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturn200OnLiveEvenWhenDatabaseFails() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection error"));

        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        PingController.register(app, dataSource);
        app.start(0);

        try {
            HttpResponse<String> responseLive = sendGetRequest(app, "/live");
            assertEquals(200, responseLive.statusCode());
            assertTrue(responseLive.body().contains("\"status\":\"alive\""));

            HttpResponse<String> responseApiLive = sendGetRequest(app, "/api/live");
            assertEquals(200, responseApiLive.statusCode());
            assertTrue(responseApiLive.body().contains("\"status\":\"alive\""));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturn200OnLiveWhenDataSourceIsNull() throws Exception {
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        PingController.register(app, null);
        app.start(0);

        try {
            HttpResponse<String> responseLive = sendGetRequest(app, "/live");
            assertEquals(200, responseLive.statusCode());
            assertTrue(responseLive.body().contains("\"status\":\"alive\""));

            HttpResponse<String> responseApiLive = sendGetRequest(app, "/api/live");
            assertEquals(200, responseApiLive.statusCode());
            assertTrue(responseApiLive.body().contains("\"status\":\"alive\""));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldCoverPrivateConstructor() throws Exception {
        java.lang.reflect.Constructor<PingController> constructor = PingController.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        PingController instance = constructor.newInstance();
        assertTrue(instance != null);
    }
}


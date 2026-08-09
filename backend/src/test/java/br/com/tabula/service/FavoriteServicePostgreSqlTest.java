package br.com.tabula.service;

import br.com.tabula.controller.FavoriteController;
import br.com.tabula.controller.StateController;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.FavoriteRepository;
import br.com.tabula.service.FavoriteService.FavoriteException;
import br.com.tabula.service.FavoriteService.RequestMetadata;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class FavoriteServicePostgreSqlTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tabula_favorites_test").withUsername("tabula").withPassword("tabula");
    static HikariDataSource dataSource;
    FavoriteService service;
    AuthenticatedPrincipal userA;
    AuthenticatedPrincipal userB;
    final RequestMetadata metadata = new RequestMetadata("127.0.0.1", "favorite-test");

    @BeforeAll static void migrateAndVerifyBackfill() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("10").load().migrate();
        try (Connection connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword()); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO usuarios(external_id,nome,email,senha_hash,role,email_verificado)
                    VALUES ('legacy-user','Legacy','legacy@test','x','USER',true)
                    """);
            statement.executeUpdate("INSERT INTO jogos(external_id,nome) VALUES ('legacy-game','Legacy Game')");
            statement.executeUpdate("""
                    INSERT INTO app_state(id,data) VALUES (1, '{"users":[
                      {"id":"legacy-user","favoriteGames":["legacy-game","missing-game","legacy-game"]},
                      {"id":"missing-user","favoriteGames":["legacy-game"]}
                    ]}'::jsonb)
                    """);
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load().migrate();
        HikariConfig config = new HikariConfig(); config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername()); config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM favoritos")) {
            assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
        }
    }
    @AfterAll static void close() { if (dataSource != null) dataSource.close(); }

    @BeforeEach void seed() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM favoritos"); statement.executeUpdate("DELETE FROM auth_tokens");
            statement.executeUpdate("DELETE FROM jogos");
            statement.executeUpdate("DELETE FROM usuarios");
            statement.executeUpdate("""
                    INSERT INTO usuarios(external_id,nome,email,senha_hash,role,email_verificado)
                    VALUES ('u1','User A','a@test','x','USER',true),('u2','User B','b@test','x','USER',true)
                    """);
            statement.executeUpdate("INSERT INTO jogos(external_id,nome) VALUES ('g1','Xadrez'),('g2','Go')");
            statement.executeUpdate("""
                    INSERT INTO auth_tokens(token,usuario_id,expires_at)
                    SELECT external_id || '-token',id,CURRENT_TIMESTAMP + INTERVAL '1 hour' FROM usuarios
                    """);
            try (ResultSet rows = statement.executeQuery("SELECT id,external_id FROM usuarios ORDER BY external_id")) {
                rows.next(); userA = new AuthenticatedPrincipal(rows.getLong(1), rows.getString(2), "USER");
                rows.next(); userB = new AuthenticatedPrincipal(rows.getLong(1), rows.getString(2), "USER");
            }
        }
        service = new FavoriteService(dataSource, new FavoriteRepository(), new AuditLogService(dataSource));
    }

    @Test void listsPersistsAndIsIdempotent() throws Exception {
        assertTrue(service.list(userA).isEmpty());
        assertTrue(service.add(userA, "g1", metadata).created());
        assertFalse(service.add(userA, "g1", metadata).created());
        assertEquals(java.util.List.of("g1"), service.list(userA));
        assertTrue(service.remove(userA, "g1", metadata));
        assertFalse(service.remove(userA, "g1", metadata));
        assertTrue(service.list(userA).isEmpty());
        assertEquals(FavoriteException.Kind.NOT_FOUND,
                assertThrows(FavoriteException.class, () -> service.add(userA, "missing", metadata)).kind());
    }

    @Test void isolatesUsersOnTheSameGame() throws Exception {
        service.add(userA, "g1", metadata); service.add(userB, "g1", metadata);
        service.remove(userA, "g1", metadata);
        assertTrue(service.list(userA).isEmpty()); assertEquals(java.util.List.of("g1"), service.list(userB));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM favoritos")) {
            assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
        }
    }

    @Test void endpointsUseAuthenticatedIdentityAndIgnoreBodyIdentity() throws Exception {
        Javalin app = Javalin.create(); FavoriteController.register(app, dataSource); app.start(0);
        try {
            assertEquals(201, request(app, "POST", "/favorites/g1", "{\"userId\":\"u2\",\"role\":\"ADMIN\"}", "u1-token").statusCode());
            assertEquals(200, request(app, "POST", "/favorites/g1", null, "u1-token").statusCode());
            HttpResponse<String> listA = request(app, "GET", "/favorites", null, "u1-token");
            HttpResponse<String> listB = request(app, "GET", "/favorites", null, "u2-token");
            assertTrue(listA.body().contains("g1")); assertFalse(listB.body().contains("g1"));
            assertEquals(401, request(app, "GET", "/favorites", null, null).statusCode());
            assertEquals(404, request(app, "POST", "/favorites/missing", null, "u1-token").statusCode());
        } finally { app.stop(); }
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT detalhes::text FROM audit_logs WHERE acao='FAVORITE_ADDED' ORDER BY id DESC LIMIT 1")) {
            assertTrue(rows.next()); assertFalse(rows.getString(1).contains("u2"));
            assertFalse(rows.getString(1).toLowerCase().contains("authorization"));
        }
    }

    @Test void relationalStateProjectsFavorites() throws Exception {
        service.add(userA, "g1", metadata);
        String state = """
                {"users":[
                  {"id":"u1","name":"User A","email":"a@test","role":"student","course":"","avatar":"A","winCount":0,"favoriteGames":[],"joinedAt":"2026-01-01","bio":""},
                  {"id":"u2","name":"User B","email":"b@test","role":"student","course":"","avatar":"B","winCount":0,"favoriteGames":["g2"],"joinedAt":"2026-01-01","bio":""}
                ],"boardGames":[
                  {"id":"g1","name":"Xadrez","description":"","coverUrl":"","category":"","minPlayers":2,"maxPlayers":2,"avgPlayTime":30,"complexity":2},
                  {"id":"g2","name":"Go","description":"","coverUrl":"","category":"","minPlayers":2,"maxPlayers":2,"avgPlayTime":30,"complexity":3}
                ]}
                """;
        Javalin app = Javalin.create(); StateController.register(app, dataSource); app.start(0);
        try {
            HttpResponse<String> get = request(app, "GET", "/state", null, null);
            assertEquals(200, get.statusCode()); assertTrue(get.body().contains("\"favoriteGames\":[\"g1\"]"));
            assertEquals(java.util.List.of("g1"), service.list(userA)); assertTrue(service.list(userB).isEmpty());
        } finally { app.stop(); }
    }

    private static HttpResponse<String> request(Javalin app, String method, String path, String body, String token)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}

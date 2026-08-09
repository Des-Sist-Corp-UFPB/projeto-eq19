package br.com.tabula.service;

import br.com.tabula.controller.SessionController;
import br.com.tabula.controller.StateController;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.SessionRepository;
import br.com.tabula.service.SessionService.RequestMetadata;
import br.com.tabula.service.SessionService.SessionException;
import br.com.tabula.service.SessionService.SessionInput;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class SessionServicePostgreSqlTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tabula_sessions_test").withUsername("tabula").withPassword("tabula");

    static HikariDataSource dataSource;
    SessionService service;
    AuthenticatedPrincipal owner;
    AuthenticatedPrincipal other;
    final RequestMetadata metadata = new RequestMetadata("127.0.0.1", "test");

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load().migrate();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
    }

    @AfterAll static void close() { if (dataSource != null) dataSource.close(); }

    @BeforeEach
    void seed() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM partida_participantes");
            statement.executeUpdate("DELETE FROM partidas");
            statement.executeUpdate("DELETE FROM auth_tokens");
            statement.executeUpdate("DELETE FROM app_state");
            statement.executeUpdate("DELETE FROM jogos");
            statement.executeUpdate("DELETE FROM usuarios");
            statement.executeUpdate("""
                    INSERT INTO usuarios (external_id,nome,email,senha_hash,role,email_verificado)
                    VALUES ('u1','Owner','owner@test','x','USER',true),
                           ('u2','Other','other@test','x','USER',true),
                           ('u3','Player','player@test','x','USER',true)
                    """);
            statement.executeUpdate("INSERT INTO jogos (external_id,nome) VALUES ('g1','Xadrez')");
            statement.executeUpdate("""
                    INSERT INTO auth_tokens (token,usuario_id,expires_at)
                    SELECT 'session-token',id,CURRENT_TIMESTAMP + INTERVAL '1 hour'
                    FROM usuarios WHERE external_id='u1'
                    """);
        }
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id,external_id FROM usuarios ORDER BY external_id")) {
            rows.next(); owner = new AuthenticatedPrincipal(rows.getLong(1), rows.getString(2), "USER");
            rows.next(); other = new AuthenticatedPrincipal(rows.getLong(1), rows.getString(2), "USER");
        }
        service = new SessionService(dataSource, new SessionRepository(dataSource), new AuditLogService(dataSource));
    }

    @Test
    void lifecycleValidationsAuthorizationAndAudit() throws Exception {
        var created = service.create(owner, input(List.of("u2"), "u2"), metadata);
        assertEquals("u1", created.organizerId());
        assertEquals(List.of("u1", "u2"), created.participantIds());
        assertEquals(created.externalId(), service.get(created.externalId()).externalId());
        assertEquals(1, service.list().size());
        assertEquals(SessionException.Kind.NOT_FOUND,
                assertThrows(SessionException.class, () -> service.get("missing")).kind());

        assertEquals(SessionException.Kind.INVALID, assertThrows(SessionException.class,
                () -> service.create(owner, input(List.of("u2", "u2"), "u2"), metadata)).kind());
        assertEquals(SessionException.Kind.INVALID, assertThrows(SessionException.class,
                () -> service.create(owner, input(List.of("u2"), "u3"), metadata)).kind());
        assertEquals(SessionException.Kind.NOT_FOUND, assertThrows(SessionException.class,
                () -> service.create(owner, new SessionInput("missing", "2026-08-01T18:00:00",
                        "Sala", List.of("u2"), "u2", 30, ""), metadata)).kind());
        assertEquals(SessionException.Kind.NOT_FOUND, assertThrows(SessionException.class,
                () -> service.create(owner, input(List.of("missing"), null), metadata)).kind());
        assertEquals(SessionException.Kind.INVALID, assertThrows(SessionException.class,
                () -> service.create(owner, new SessionInput("g1", "invalid", "Sala",
                        List.of("u2"), null, 30, ""), metadata)).kind());
        assertEquals(SessionException.Kind.INVALID, assertThrows(SessionException.class,
                () -> service.create(owner, new SessionInput("g1", "2026-08-01T18:00:00", "Sala",
                        List.of("u2"), null, -1, ""), metadata)).kind());

        assertEquals(SessionException.Kind.FORBIDDEN, assertThrows(SessionException.class,
                () -> service.delete(other, created.externalId(), metadata)).kind());
        service.delete(owner, created.externalId(), metadata);
        assertTrue(service.list().isEmpty());
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM audit_logs")) {
            rows.next();
            assertTrue(rows.getInt(1) >= 2);
        }
    }

    @Test
    void exposesAuthenticatedEndpointsAndDoesNotTrustOrganizerPayload() throws Exception {
        io.javalin.Javalin app = io.javalin.Javalin.create();
        SessionController.register(app, dataSource);
        app.start(0);
        try {
            assertEquals(401, request(app, "GET", "/sessions", null, null).statusCode());
            String body = """
                    {"gameId":"g1","date":"2026-08-01T18:00:00","location":"Sala",
                     "participantIds":["u2"],"winnerId":"u2","duration":30,"notes":"ok",
                     "organizerId":"u3"}
                    """;
            assertEquals(422, request(app, "POST", "/sessions", body, "session-token").statusCode());
            String validBody = """
                    {"gameId":"g1","date":"2026-08-01T18:00:00","location":"Sala",
                     "participantIds":["u2"],"winnerId":"u2","duration":30,"notes":"ok"}
                    """;
            HttpResponse<String> created = request(app, "POST", "/sessions", validBody, "session-token");
            assertEquals(201, created.statusCode(), created.body());
            assertTrue(created.body().contains("\"organizerId\":\"u1\""));
            String id = created.body().replaceFirst(".*?\"id\":\"([^\"]+)\".*", "$1");
            assertEquals(200, request(app, "GET", "/sessions", null, "session-token").statusCode());
            assertEquals(200, request(app, "GET", "/sessions/" + id, null, "session-token").statusCode());
            assertEquals(404, request(app, "GET", "/sessions/missing", null, "session-token").statusCode());
            assertEquals(422, request(app, "POST", "/sessions", "{", "session-token").statusCode());
            assertEquals(204, request(app, "DELETE", "/sessions/" + id, null, "session-token").statusCode());
            assertEquals(404, request(app, "GET", "/sessions/" + id, null, "session-token").statusCode());
        } finally {
            app.stop();
        }
    }

    @Test
    void normalShadowSyncCannotRestoreOrOverwriteSessions() throws Exception {
        var created = service.create(owner, input(List.of("u2"), "u2"), metadata);
        String oldState = """
                {"users":[],"boardGames":[],"sessions":[
                  {"id":"legacy","gameId":"g1","date":"2020-01-01T00:00:00","location":"old",
                   "organizerId":"u2","participantIds":["u2"],"winnerId":"u2","duration":1,
                   "notes":"old","photos":[],"comments":[]}
                ],"events":[]}
                """;
        RelationalStateSyncService.syncFromStateJson(dataSource, oldState);
        assertEquals(1, service.list().size());
        assertEquals(created.externalId(), service.list().get(0).externalId());
    }

    @Test
    void deletedSessionNeverReappearsFromLegacyStateOrLaterValidPut() throws Exception {
        var created = service.create(owner, input(List.of("u2"), "u2"), metadata);
        String legacyState = stateJson(created.externalId(), "Curso original", "Boa partida");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO app_state (id, data) VALUES (1, ?::jsonb)")) {
            statement.setString(1, legacyState);
            statement.executeUpdate();
        }
        service.delete(owner, created.externalId(), metadata);

        io.javalin.Javalin app = io.javalin.Javalin.create();
        SessionController.register(app, dataSource);
        StateController.register(app, dataSource);
        app.start(0);
        try {
            HttpResponse<String> sessions = request(app, "GET", "/sessions", null, "session-token");
            assertEquals(200, sessions.statusCode());
            assertFalse(sessions.body().contains(created.externalId()));

            HttpResponse<String> projectedBeforePut = request(app, "GET", "/state", null, null);
            assertEquals(200, projectedBeforePut.statusCode());
            assertFalse(projectedBeforePut.body().contains(created.externalId()));

            String legitimateLegacyUpdate = stateWithoutSessions("Curso atualizado");
            HttpResponse<String> accepted = request(
                    app, "PUT", "/state", legitimateLegacyUpdate, "session-token");
            assertEquals(200, accepted.statusCode(), accepted.body());
            assertTrue(service.list().isEmpty());

            HttpResponse<String> projectedAfterPut = request(app, "GET", "/state", null, null);
            assertEquals(200, projectedAfterPut.statusCode());
            assertFalse(projectedAfterPut.body().contains(created.externalId()));

            String divergentSnapshot = stateJson(created.externalId(), "Curso rejeitado", "Adulterada");
            HttpResponse<String> rejected = request(
                    app, "PUT", "/state", divergentSnapshot, "session-token");
            assertEquals(409, rejected.statusCode(), rejected.body());
            assertTrue(service.list().isEmpty());
        } finally {
            app.stop();
        }

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertEquals(0, count(statement, "partidas"));
            try (ResultSet row = statement.executeQuery(
                    "SELECT data->'users'->0->>'course' FROM app_state WHERE id = 1")) {
                assertTrue(row.next());
                assertEquals("", row.getString(1));
            }
        }
    }

    private static SessionInput input(List<String> participants, String winner) {
        return new SessionInput("g1", "2026-08-01T18:00:00", "Sala",
                participants, winner, 45, "Boa partida");
    }

    private static int count(Statement statement, String table) throws Exception {
        try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static String stateWithoutSessions(String course) {
        return """
                {"users":[
                  {"id":"u1","name":"Owner","email":"owner@test","role":"student","course":"%s",
                   "avatar":"O","winCount":0,"favoriteGames":[],"joinedAt":"2026-01-01","bio":""},
                  {"id":"u2","name":"Other","email":"other@test","role":"student","course":"",
                   "avatar":"O","winCount":0,"favoriteGames":[],"joinedAt":"2026-01-01","bio":""},
                  {"id":"u3","name":"Player","email":"player@test","role":"student","course":"",
                   "avatar":"P","winCount":0,"favoriteGames":[],"joinedAt":"2026-01-01","bio":""}
                ],"boardGames":[
                  {"id":"g1","name":"Xadrez","description":"","coverUrl":"","category":"",
                   "minPlayers":2,"maxPlayers":4,"avgPlayTime":30,"complexity":2}
                ],"events":[]}
                """.formatted(course);
    }

    private static String stateJson(String sessionId, String course, String notes) {
        String base = stateWithoutSessions(course);
        return base.substring(0, base.lastIndexOf('}')) + """
                ,"sessions":[
                  {"id":"%s","gameId":"g1","date":"2026-08-01T18:00:00","location":"Sala",
                   "organizerId":"u1","participantIds":["u1","u2"],"winnerId":"u2",
                   "duration":45,"notes":"%s","photos":[],"comments":[]}
                ]}
                """.formatted(sessionId, notes);
    }

    private static HttpResponse<String> request(io.javalin.Javalin app, String method, String path,
                                                String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}

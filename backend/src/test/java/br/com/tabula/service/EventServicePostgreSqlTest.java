package br.com.tabula.service;

import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.controller.EventController;
import br.com.tabula.controller.StateController;
import br.com.tabula.repository.EventRepository;
import br.com.tabula.service.EventService.CompletionInput;
import br.com.tabula.service.EventService.EventException;
import br.com.tabula.service.EventService.EventInput;
import br.com.tabula.service.EventService.RequestMetadata;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class EventServicePostgreSqlTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("tabula_events_test")
                    .withUsername("tabula")
                    .withPassword("tabula");

    private static HikariDataSource dataSource;
    private EventService service;
    private final RequestMetadata metadata = new RequestMetadata("127.0.0.1", "test");
    private final List<AuthenticatedPrincipal> users = new ArrayList<>();

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load().migrate();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setMaximumPoolSize(8);
        dataSource = new HikariDataSource(config);
    }

    @AfterAll
    static void close() {
        if (dataSource != null) dataSource.close();
    }

    @BeforeEach
    void seed() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM comentarios");
            statement.executeUpdate("DELETE FROM partida_fotos");
            statement.executeUpdate("DELETE FROM partida_participantes");
            statement.executeUpdate("DELETE FROM partidas");
            statement.executeUpdate("DELETE FROM evento_participantes");
            statement.executeUpdate("DELETE FROM eventos");
            statement.executeUpdate("DELETE FROM favoritos");
            statement.executeUpdate("DELETE FROM auth_tokens");
            statement.executeUpdate("DELETE FROM jogos");
            statement.executeUpdate("DELETE FROM usuarios");
            for (int i = 1; i <= 6; i++) {
                statement.executeUpdate("""
                        INSERT INTO usuarios (external_id, nome, email, senha_hash, role, email_verificado)
                        VALUES ('u%s', 'User %s', 'u%s@example.com', 'hash', 'USER', TRUE)
                        """.formatted(i, i, i));
            }
            statement.executeUpdate("""
                    INSERT INTO jogos (external_id, nome) VALUES ('g1', 'Xadrez'), ('g2', 'Magic')
                    """);
        }
        users.clear();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT id, external_id FROM usuarios ORDER BY external_id")) {
            while (resultSet.next()) {
                users.add(new AuthenticatedPrincipal(
                        resultSet.getLong("id"), resultSet.getString("external_id"), "USER"));
            }
        }
        service = new EventService(dataSource, new EventRepository(dataSource), new AuditLogService(dataSource));
    }

    @Test
    void coversLifecycleAuthorizationQueueCompletionAndAudit() throws Exception {
        EventInput input = input(2);
        var created = service.create(users.get(0), input, metadata);
        assertEquals("u1", created.organizerExternalId());
        assertEquals(List.of("u1"), created.participantIds());
        assertEquals(1, service.list().size());
        assertEquals(created.externalId(), service.get(created.externalId()).externalId());
        assertEquals(EventException.Kind.NOT_FOUND,
                assertThrows(EventException.class, () -> service.get("missing")).kind());

        EventException forbidden = assertThrows(EventException.class, () ->
                service.update(users.get(1), created.externalId(), input, metadata));
        assertEquals(EventException.Kind.FORBIDDEN, forbidden.kind());
        service.auditRejected(users.get(1), created.externalId(), forbidden.reason(), metadata);

        var updated = service.update(users.get(0), created.externalId(),
                new EventInput("g2", "2026-08-02", "19:30", "Sala B", 2, "Atualizado"), metadata);
        assertEquals("g2", updated.gameExternalId());

        assertFalse(service.join(users.get(1), created.externalId(), metadata).waitlisted());
        assertTrue(service.join(users.get(2), created.externalId(), metadata).waitlisted());
        assertEquals(EventException.Kind.CONFLICT,
                assertThrows(EventException.class,
                        () -> service.join(users.get(2), created.externalId(), metadata)).kind());

        var leave = service.leave(users.get(1), created.externalId(), metadata);
        assertTrue(leave.promoted());
        assertTrue(leave.event().participantIds().contains("u3"));
        assertEquals(EventException.Kind.INVALID,
                assertThrows(EventException.class,
                        () -> service.leave(users.get(0), created.externalId(), metadata)).kind());

        var completed = service.complete(users.get(0), created.externalId(),
                new CompletionInput("u3", 60, "Boa partida", "Parabéns", "photo.png"), metadata);
        assertEquals("completed", completed.status());
        assertEquals(EventException.Kind.CONFLICT,
                assertThrows(EventException.class, () -> service.complete(users.get(0), created.externalId(),
                        new CompletionInput("u3", 60, "again", null, null), metadata)).kind());

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertEquals(1, count(statement, "partidas"));
            assertEquals(1, count(statement, "comentarios"));
            assertEquals(1, count(statement, "partida_fotos"));
            assertTrue(count(statement, "audit_logs") >= 7);
        }
    }

    @Test
    void serializesConcurrentJoinsWithoutExceedingCapacity() throws Exception {
        var event = service.create(users.get(0), input(2), metadata);
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = List.of(
                    () -> service.join(users.get(1), event.externalId(), metadata).waitlisted(),
                    () -> service.join(users.get(2), event.externalId(), metadata).waitlisted(),
                    () -> service.join(users.get(3), event.externalId(), metadata).waitlisted(),
                    () -> service.join(users.get(4), event.externalId(), metadata).waitlisted()
            );
            var futures = executor.invokeAll(tasks);
            int waitlisted = 0;
            for (var future : futures) if (future.get()) waitlisted++;
            assertEquals(3, waitlisted);
        } finally {
            executor.shutdownNow();
        }
        var current = service.get(event.externalId());
        assertEquals(2, current.participantIds().size());
        assertEquals(3, current.waitingListIds().size());
        assertEquals(current.waitingListIds().size(),
                current.waitingListIds().stream().distinct().count());
    }

    @Test
    void concurrentHttpCompletionCreatesExactlyOneSessionAndRejectsTheOther() throws Exception {
        var event = service.create(users.get(0), input(3), metadata);
        service.join(users.get(1), event.externalId(), metadata);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO auth_tokens (token, usuario_id, expires_at)
                     VALUES ('concurrent-complete-token', ?, CURRENT_TIMESTAMP + INTERVAL '1 hour')
                     """)) {
            statement.setLong(1, users.get(0).getDatabaseId());
            statement.executeUpdate();
        }
        int sessionsCreatedBefore;
        int eventsCompletedBefore;
        int eventsRejectedBefore;
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            sessionsCreatedBefore = scalar(statement,
                    "SELECT COUNT(*) FROM audit_logs WHERE acao = 'SESSION_CREATED' AND sucesso = TRUE");
            eventsCompletedBefore = scalar(statement,
                    "SELECT COUNT(*) FROM audit_logs WHERE acao = 'EVENT_COMPLETED' AND sucesso = TRUE");
            eventsRejectedBefore = scalar(statement,
                    "SELECT COUNT(*) FROM audit_logs WHERE acao = 'EVENT_OPERATION_REJECTED' AND sucesso = FALSE");
        }

        io.javalin.Javalin app = io.javalin.Javalin.create();
        EventController.register(app, dataSource);
        app.start(0);
        var executor = Executors.newFixedThreadPool(2);
        try {
            String completion = """
                    {"winnerId":"u1","duration":45,"notes":"Concluída uma vez"}
                    """;
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<HttpResponse<String>> request = () -> {
                ready.countDown();
                start.await();
                return request(app, "POST", "/events/" + event.externalId() + "/complete",
                        completion, "concurrent-complete-token");
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(
                    first.get().statusCode(), second.get().statusCode()));
            statuses.sort(Integer::compareTo);
            assertEquals(List.of(200, 409), statuses);
        } finally {
            executor.shutdownNow();
            app.stop();
        }

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertEquals(1, count(statement, "partidas"));
            assertEquals(2, count(statement, "partida_participantes"));
            assertEquals(1, scalar(statement, """
                    SELECT COUNT(*) FROM eventos WHERE external_id = '%s' AND status = 'completed'
                    """.formatted(event.externalId())));
            assertEquals(sessionsCreatedBefore + 1, scalar(statement, """
                    SELECT COUNT(*) FROM audit_logs
                    WHERE acao = 'SESSION_CREATED' AND sucesso = TRUE
                    """));
            assertEquals(eventsCompletedBefore + 1, scalar(statement, """
                    SELECT COUNT(*) FROM audit_logs
                    WHERE acao = 'EVENT_COMPLETED' AND sucesso = TRUE
                    """));
            assertEquals(eventsRejectedBefore + 1, scalar(statement, """
                    SELECT COUNT(*) FROM audit_logs
                    WHERE acao = 'EVENT_OPERATION_REJECTED' AND sucesso = FALSE
                    """));
        }
    }

    @Test
    void rollsBackInvalidCompletionAndSupportsCancellation() throws Exception {
        var event = service.create(users.get(0), input(3), metadata);
        assertEquals(EventException.Kind.INVALID,
                assertThrows(EventException.class, () -> service.complete(users.get(0), event.externalId(),
                        new CompletionInput("u6", 30, "invalid", null, null), metadata)).kind());
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertEquals(0, count(statement, "partidas"));
        }
        assertEquals(EventException.Kind.FORBIDDEN,
                assertThrows(EventException.class,
                        () -> service.cancel(users.get(1), event.externalId(), metadata)).kind());
        assertEquals("cancelled",
                service.cancel(users.get(0), event.externalId(), metadata).status());
        assertEquals(EventException.Kind.CONFLICT,
                assertThrows(EventException.class,
                        () -> service.cancel(users.get(0), event.externalId(), metadata)).kind());
    }

    @Test
    void validatesPayloadAndMissingGame() {
        assertEquals(EventException.Kind.INVALID,
                assertThrows(EventException.class,
                        () -> service.create(users.get(0), input(0), metadata)).kind());
        assertEquals(EventException.Kind.INVALID,
                assertThrows(EventException.class, () -> service.create(users.get(0),
                        new EventInput("missing", "2026-08-01", "18:00", "Sala", 2, ""), metadata)).kind());
        assertNotNull(service);
    }

    @Test
    void exposesAuthenticatedEndpointsAndRejectsLegacyEventOverwrite() throws Exception {
        var event = service.create(users.get(0), input(2), metadata);
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO auth_tokens (token, usuario_id, expires_at)
                    VALUES ('event-token', ?, CURRENT_TIMESTAMP + INTERVAL '1 hour')
                    """)) {
                statement.setLong(1, users.get(0).getDatabaseId());
                statement.executeUpdate();
            }
        }

        io.javalin.Javalin app = io.javalin.Javalin.create();
        EventController.register(app, dataSource);
        StateController.register(app, dataSource);
        app.start(0);
        try {
            assertEquals(401, request(app, "GET", "/events", null, null).statusCode());
            HttpResponse<String> list = request(app, "GET", "/events", null, "event-token");
            assertEquals(200, list.statusCode());
            assertTrue(list.body().contains(event.externalId()));

            HttpResponse<String> get = request(app, "GET", "/events/" + event.externalId(), null, "event-token");
            assertEquals(200, get.statusCode());
            assertTrue(get.body().contains("\"organizerId\":\"u1\""));
            assertEquals(404, request(app, "GET", "/events/missing", null, "event-token").statusCode());

            String forged = """
                    {"gameId":"g1","date":"2026-09-01","time":"20:00","location":"Sala Nova",
                     "maxParticipants":4,"description":"Novo","organizerId":"u6"}
                    """;
            assertEquals(422, request(app, "POST", "/events", forged, "event-token").statusCode());
            String valid = """
                    {"gameId":"g1","date":"2026-09-01","time":"20:00","location":"Sala Nova",
                     "maxParticipants":4,"description":"Novo"}
                    """;
            HttpResponse<String> create = request(app, "POST", "/events", valid, "event-token");
            assertEquals(201, create.statusCode());
            assertTrue(create.body().contains("\"organizerId\":\"u1\""));

            assertEquals("Sala A", service.get(event.externalId()).location());
        } finally {
            app.stop();
        }
    }

    private static EventInput input(int capacity) {
        return new EventInput("g1", "2026-08-01", "18:00", "Sala A", capacity, "Mesa aberta");
    }

    private static int count(Statement statement, String table) throws Exception {
        return scalar(statement, "SELECT COUNT(*) FROM " + table);
    }

    private static int scalar(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static HttpResponse<String> request(io.javalin.Javalin app, String method, String path,
                                                String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://localhost:" + app.port() + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String stateJson(String eventId, String location) {
        return """
                {"users":[
                  {"id":"u1","name":"User 1","email":"u1@example.com","role":"student","course":"",
                   "avatar":"U","winCount":0,"favoriteGames":[],"joinedAt":"2026-01-01","bio":""}
                ],"boardGames":[
                  {"id":"g1","name":"Xadrez","description":"","coverUrl":"","category":"",
                   "minPlayers":2,"maxPlayers":4,"avgPlayTime":30,"complexity":2}
                ],"sessions":[],"events":[
                  {"id":"%s","gameId":"g1","date":"2026-08-01","time":"18:00","location":"%s",
                   "maxParticipants":2,"participantIds":["u1"],"waitingListIds":[],
                   "description":"Mesa aberta","organizerId":"u1","status":"active"}
                ]}
                """.formatted(eventId, location);
    }
}

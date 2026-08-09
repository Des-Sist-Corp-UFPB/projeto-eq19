package br.com.tabula.service;

import br.com.tabula.controller.CommentController;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.CommentRepository;
import br.com.tabula.service.CommentService.CommentException;
import br.com.tabula.service.CommentService.RequestMetadata;
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
class CommentServicePostgreSqlTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tabula_comments_test").withUsername("tabula").withPassword("tabula");
    static HikariDataSource dataSource;
    CommentService service;
    AuthenticatedPrincipal owner;
    AuthenticatedPrincipal other;
    AuthenticatedPrincipal admin;
    final RequestMetadata metadata = new RequestMetadata("127.0.0.1", "test-agent");

    @BeforeAll static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load().migrate();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword()); dataSource = new HikariDataSource(config);
    }
    @AfterAll static void close() { if (dataSource != null) dataSource.close(); }

    @BeforeEach void seed() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM comentarios");
            statement.executeUpdate("DELETE FROM partida_participantes");
            statement.executeUpdate("DELETE FROM partidas");
            statement.executeUpdate("DELETE FROM auth_tokens");
            statement.executeUpdate("DELETE FROM jogos");
            statement.executeUpdate("DELETE FROM usuarios");
            statement.executeUpdate("""
                    INSERT INTO usuarios (external_id,nome,email,senha_hash,role,email_verificado)
                    VALUES ('u1','Owner','owner@test','x','USER',true),
                           ('u2','Other','other@test','x','USER',true),
                           ('admin','Admin','admin@test','x','ADMIN',true)
                    """);
            statement.executeUpdate("INSERT INTO jogos (external_id,nome) VALUES ('g1','Xadrez')");
            statement.executeUpdate("""
                    INSERT INTO partidas (external_id,jogo_id,data_hora,local,organizador_id,duracao_minutos)
                    SELECT 's1',j.id,CURRENT_TIMESTAMP,'Sala',u.id,30 FROM jogos j,usuarios u
                    WHERE j.external_id='g1' AND u.external_id='u1'
                    """);
            statement.executeUpdate("""
                    INSERT INTO auth_tokens(token,usuario_id,expires_at)
                    SELECT external_id || '-token',id,CURRENT_TIMESTAMP + INTERVAL '1 hour' FROM usuarios
                    """);
            try (ResultSet rows = statement.executeQuery("SELECT id,external_id,role FROM usuarios ORDER BY external_id")) {
                while (rows.next()) {
                    var principal = new AuthenticatedPrincipal(rows.getLong(1), rows.getString(2), rows.getString(3));
                    switch (rows.getString(2)) { case "u1" -> owner = principal; case "u2" -> other = principal; default -> admin = principal; }
                }
            }
        }
        service = new CommentService(dataSource, new CommentRepository(), new AuditLogService(dataSource));
    }

    @Test void createsListsValidatesAndPersistsBackendIdentity() throws Exception {
        var created = service.create(owner, "s1", "  comentário seguro  ", metadata);
        assertEquals("u1", created.userId());
        assertEquals("Owner", created.userName());
        assertEquals("comentário seguro", created.content());
        assertEquals(1, service.list("s1").size());
        assertEquals(CommentException.Kind.NOT_FOUND,
                assertThrows(CommentException.class, () -> service.create(owner, "missing", "x", metadata)).kind());
        assertEquals(CommentException.Kind.INVALID,
                assertThrows(CommentException.class, () -> service.create(owner, "s1", " ", metadata)).kind());
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM comentarios")) {
            assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
        }
    }

    @Test void enforcesOwnerAdminAndNotFoundDeletion() throws Exception {
        var own = service.create(owner, "s1", "own", metadata);
        assertEquals(CommentException.Kind.FORBIDDEN, assertThrows(CommentException.class,
                () -> service.delete(other, "s1", own.externalId(), metadata)).kind());
        service.delete(admin, "s1", own.externalId(), metadata);
        assertTrue(service.list("s1").isEmpty());
        assertEquals(CommentException.Kind.NOT_FOUND, assertThrows(CommentException.class,
                () -> service.delete(owner, "s1", "missing", metadata)).kind());
    }

    @Test void endpointIgnoresSpoofedAuthorFieldsAndAuditsWithoutContent() throws Exception {
        Javalin app = Javalin.create(); CommentController.register(app, dataSource); app.start(0);
        try {
            HttpResponse<String> created = request(app, "POST", "/sessions/s1/comments",
                    "{\"content\":\"secret body\",\"userId\":\"u2\",\"role\":\"ADMIN\"}", "u1-token");
            assertEquals(201, created.statusCode(), created.body());
            assertTrue(created.body().contains("\"userId\":\"u1\""));
            assertFalse(created.body().contains("u2"));
            assertEquals(401, request(app, "POST", "/sessions/s1/comments", "{\"content\":\"x\"}", null).statusCode());
        } finally { app.stop(); }
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT detalhes::text FROM audit_logs WHERE acao='COMMENT_CREATED' ORDER BY id DESC LIMIT 1")) {
            assertTrue(rows.next()); assertFalse(rows.getString(1).contains("secret body"));
            assertTrue(rows.getString(1).contains("parentId"));
        }
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

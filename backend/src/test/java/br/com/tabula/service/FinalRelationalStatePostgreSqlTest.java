package br.com.tabula.service;

import br.com.tabula.controller.StateController;
import br.com.tabula.controller.AdminUserController;
import br.com.tabula.controller.ProfileController;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class FinalRelationalStatePostgreSqlTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tabula_final_state_test").withUsername("tabula").withPassword("tabula");

    @Test
    void upgradeFromV13DropsOnlyLegacyStateAndKeepsRelationalDomainsOperational() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("13").load().migrate();
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO usuarios(external_id,nome,email,senha_hash,role,email_verificado,curso,bio,avatar_url) VALUES('final-user','Final User','final@test','hash','ADMIN',true,'Course','Bio','/avatar.png')");
            statement.executeUpdate("INSERT INTO jogos(external_id,nome,descricao,cover_url,categoria,min_players,max_players,avg_play_time,complexity) VALUES('final-game','Final Game','Description','/cover.png','Strategy',2,4,30,2.5)");
            statement.executeUpdate("INSERT INTO eventos(external_id,jogo_id,data_hora,local,descricao,max_participantes,status,organizador_id) SELECT 'final-event',j.id,CURRENT_TIMESTAMP,'Room','Event',4,'active',u.id FROM jogos j,usuarios u WHERE j.external_id='final-game' AND u.external_id='final-user'");
            statement.executeUpdate("INSERT INTO evento_participantes(evento_id,usuario_id,tipo) SELECT e.id,u.id,'PARTICIPANT' FROM eventos e,usuarios u WHERE e.external_id='final-event' AND u.external_id='final-user'");
            statement.executeUpdate("INSERT INTO partidas(external_id,jogo_id,data_hora,local,organizador_id,vencedor_id,duracao_minutos,notas) SELECT 'final-session',j.id,CURRENT_TIMESTAMP,'Room',u.id,u.id,30,'Notes' FROM jogos j,usuarios u WHERE j.external_id='final-game' AND u.external_id='final-user'");
            statement.executeUpdate("INSERT INTO partida_participantes(partida_id,usuario_id) SELECT p.id,u.id FROM partidas p,usuarios u WHERE p.external_id='final-session' AND u.external_id='final-user'");
            statement.executeUpdate("INSERT INTO comentarios(external_id,partida_id,usuario_id,conteudo) SELECT 'final-comment',p.id,u.id,'Comment' FROM partidas p,usuarios u WHERE p.external_id='final-session' AND u.external_id='final-user'");
            statement.executeUpdate("INSERT INTO favoritos(usuario_id,jogo_id) SELECT u.id,j.id FROM usuarios u,jogos j WHERE u.external_id='final-user' AND j.external_id='final-game'");
            statement.executeUpdate("INSERT INTO app_state(id,data) VALUES(1,'{\"users\":[],\"boardGames\":[]}'::jsonb)");
        }

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();
        HikariConfig config = new HikariConfig(); config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername()); config.setPassword(POSTGRES.getPassword());
        try (HikariDataSource dataSource = new HikariDataSource(config);
             Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("SELECT to_regclass('public.app_state')")) {
                assertTrue(rows.next()); assertNull(rows.getString(1));
            }
            assertEquals(1, count(statement, "usuarios", "external_id='final-user'"));
            assertEquals(1, count(statement, "jogos", "external_id='final-game'"));
            assertEquals(1, count(statement, "eventos", "external_id='final-event'"));
            assertEquals(1, count(statement, "partidas", "external_id='final-session'"));
            assertEquals(1, count(statement, "comentarios", "external_id='final-comment'"));
            assertEquals(1, count(statement, "favoritos", "TRUE"));

            statement.executeUpdate("INSERT INTO usuarios(external_id,nome,email,senha_hash,role,email_verificado) VALUES('ordinary-user','Ordinary','ordinary@test','hash','USER',true),('delete-user','Delete','delete@test','hash','USER',true)");
            statement.executeUpdate("INSERT INTO auth_tokens(token,usuario_id,expires_at) SELECT external_id||'-token',id,CURRENT_TIMESTAMP+INTERVAL '1 hour' FROM usuarios WHERE external_id IN('final-user','ordinary-user')");

            Javalin app = Javalin.create(); StateController.register(app, dataSource);
            AdminUserController.register(app, dataSource); ProfileController.register(app, dataSource); app.start(0);
            try {
                HttpResponse<String> state = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + app.port() + "/state")).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, state.statusCode());
                assertTrue(state.body().contains("final-user")); assertTrue(state.body().contains("final-game"));
                assertTrue(state.body().contains("final-event")); assertTrue(state.body().contains("final-session"));
                assertTrue(state.body().contains("final-comment"));
                HttpResponse<String> put = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + app.port() + "/state")).PUT(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(404, put.statusCode());
                assertEquals(401, call(app, "GET", "/profile", null, null).statusCode());
                HttpResponse<String> profile = call(app, "GET", "/profile", null, "final-user-token");
                assertEquals(200, profile.statusCode());
                assertTrue(profile.body().contains("Final User"));
                assertEquals(422, call(app, "PUT", "/profile", "{\"name\":\"\"}", "final-user-token").statusCode());
                HttpResponse<String> updatedProfile = call(app, "PUT", "/profile",
                        "{\"name\":\"Final Updated\",\"course\":\"New Course\",\"bio\":\"New Bio\",\"avatarUrl\":\"https://example.test/avatar.png\"}",
                        "final-user-token");
                assertEquals(200, updatedProfile.statusCode());
                assertTrue(updatedProfile.body().contains("Final Updated"));
                assertEquals(401, call(app, "PATCH", "/users/delete-user/role", "{\"role\":\"admin\"}", null).statusCode());
                assertEquals(403, call(app, "PATCH", "/users/delete-user/role", "{\"role\":\"admin\"}", "ordinary-user-token").statusCode());
                assertEquals(422, call(app, "PATCH", "/users/delete-user/role", "{\"role\":\"owner\"}", "final-user-token").statusCode());
                assertEquals(404, call(app, "PATCH", "/users/missing/role", "{\"role\":\"admin\"}", "final-user-token").statusCode());
                assertEquals(200, call(app, "PATCH", "/users/delete-user/role", "{\"role\":\"admin\"}", "final-user-token").statusCode());
                assertEquals(409, call(app, "DELETE", "/users/final-user", null, "final-user-token").statusCode());
                assertEquals(204, call(app, "DELETE", "/users/delete-user", null, "final-user-token").statusCode());
                assertEquals(0, count(statement, "usuarios", "external_id='delete-user'"));
            } finally { app.stop(); }
        }
    }

    private static int count(Statement statement, String table, String where) throws Exception {
        try (ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + table + " WHERE " + where)) {
            rows.next(); return rows.getInt(1);
        }
    }

    private static HttpResponse<String> call(Javalin app, String method, String path, String body, String token)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}

package br.com.tabula.controller;

import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class StateController {
    private static final Logger LOGGER = LoggerFactory.getLogger(StateController.class);

    private StateController() {
    }

    private static boolean isRelationalReadEnabled() {
        String value = System.getenv("RELATIONAL_STATE_READ_ENABLED");
        if (value == null || value.isBlank()) {
            value = System.getProperty("RELATIONAL_STATE_READ_ENABLED");
        }
        return "true".equalsIgnoreCase(value);
    }

    public static void register(Javalin app, HikariDataSource dataSource) {
        app.get("/state", ctx -> {
            try {
                String payload = null;
                if (isRelationalReadEnabled()) {
                    try {
                        payload = br.com.tabula.service.RelationalStateReadService.readStateAsJson(dataSource);
                    } catch (Exception ex) {
                        LOGGER.error("Failed to read relational database state, falling back to app_state", ex);
                    }
                }

                if (payload == null || payload.isBlank()) {
                    payload = readState(dataSource);
                }

                if (payload == null || payload.isBlank()) {
                    ctx.status(404).json(Map.of("error", "Estado ainda não inicializado."));
                    return;
                }
                ctx.contentType("application/json").result(payload);
            } catch (SQLException ex) {
                LOGGER.error("Failed to read app state", ex);
                ctx.status(500).json(Map.of("error", "Não foi possível carregar os dados."));
            }
        });

        app.put("/state", ctx -> {
            String payload = ctx.body();
            if (payload == null || payload.isBlank()) {
                ctx.status(400).json(Map.of("error", "Payload vazio."));
                return;
            }

            try {
                if (stateExists(dataSource) && !hasValidBearerToken(dataSource, ctx)) {
                    ctx.status(401).json(Map.of("error", "Sessão inválida ou expirada."));
                    return;
                }

                saveState(dataSource, payload);
                try {
                    br.com.tabula.service.RelationalStateSyncService.syncFromStateJson(dataSource, payload);
                } catch (Exception ex) {
                    LOGGER.error("Failed to sync relational database state in shadow mode", ex);
                }
                ctx.json(Map.of("ok", true));
            } catch (SQLException ex) {
                LOGGER.error("Failed to save app state", ex);
                ctx.status(500).json(Map.of("error", "Não foi possível salvar os dados."));
            }
        });
    }

    private static String readState(HikariDataSource dataSource) throws SQLException {
        String sql = "SELECT data::text FROM app_state WHERE id = 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) return resultSet.getString(1);
            return null;
        }
    }

    private static boolean stateExists(HikariDataSource dataSource) throws SQLException {
        String sql = "SELECT 1 FROM app_state WHERE id = 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next();
        }
    }

    private static boolean hasValidBearerToken(HikariDataSource dataSource, Context ctx) throws SQLException {
        String authorization = ctx.header("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) return false;

        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isBlank()) return false;

        String sql = "SELECT 1 FROM auth_tokens WHERE token = ? AND expires_at > CURRENT_TIMESTAMP";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, token);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static void saveState(HikariDataSource dataSource, String payload) throws SQLException {
        String sql = """
                INSERT INTO app_state (id, data, updated_at)
                VALUES (1, ?::jsonb, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO UPDATE
                SET data = EXCLUDED.data,
                    updated_at = CURRENT_TIMESTAMP
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, payload);
            statement.executeUpdate();
        }
    }
}

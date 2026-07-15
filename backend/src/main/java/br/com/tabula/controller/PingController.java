package br.com.tabula.controller;

import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

public class PingController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PingController.class);

    private PingController() {
    }

    public static void register(Javalin app, HikariDataSource dataSource) {
        app.get("/ping", ctx -> {
            LOGGER.info("GET /ping requested");
            checkDatabaseHealth(ctx, dataSource);
        });

        app.get("/api/ping", ctx -> {
            LOGGER.info("GET /api/ping requested");
            checkDatabaseHealth(ctx, dataSource);
        });

        app.get("/live", ctx -> {
            LOGGER.info("GET /live requested");
            ctx.status(200).json(Map.of(
                    "status", "alive"
            ));
        });

        app.get("/api/live", ctx -> {
            LOGGER.info("GET /api/live requested");
            ctx.status(200).json(Map.of(
                    "status", "alive"
            ));
        });
    }

    private static void checkDatabaseHealth(Context ctx, HikariDataSource dataSource) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1");
             ResultSet rs = stmt.executeQuery()) {
            
            ctx.status(200).json(Map.of(
                    "status", "ok",
                    "database", "up"
            ));
        } catch (Exception e) {
            LOGGER.error("Database healthcheck failed", e);
            ctx.status(503).json(Map.of(
                    "status", "error",
                    "database", "down",
                    "error", "Database unavailable"
            ));
        }
    }
}


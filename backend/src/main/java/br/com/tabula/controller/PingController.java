package br.com.tabula.controller;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

public class PingController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PingController.class);

    private PingController() {
    }

    public static void register(Javalin app) {
        io.javalin.http.Handler pingHandler = ctx -> {
            LOGGER.info("GET /ping requested");
            ctx.status(200).json(Map.of(
                    "status", "ok",
                    "service", "eq19",
                    "timestamp", Instant.now().toString()
            ));
        };
        app.get("/ping", pingHandler);
        app.get("/api/ping", pingHandler);
    }
}

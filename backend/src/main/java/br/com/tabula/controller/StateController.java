package br.com.tabula.controller;

import br.com.tabula.service.RelationalStateReadService;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** Agregado de compatibilidade para o cache do frontend. Ele nunca persiste estado. */
public final class StateController {
    private static final Logger LOGGER = LoggerFactory.getLogger(StateController.class);
    private StateController() {}

    public static void register(Javalin app, HikariDataSource dataSource) {
        app.get("/state", ctx -> {
            try {
                ctx.contentType("application/json").result(RelationalStateReadService.readStateAsJson(dataSource));
            } catch (Exception ex) {
                LOGGER.atError().addKeyValue("operation", "relational_state_read").setCause(ex)
                        .log("Failed to build relational state projection");
                ctx.status(500).json(Map.of("error", "Não foi possível carregar os dados."));
            }
        });
    }
}

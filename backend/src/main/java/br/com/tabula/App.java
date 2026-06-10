package br.com.tabula;

import br.com.tabula.config.DatabaseConfig;
import br.com.tabula.controller.AuthController;
import br.com.tabula.controller.PingController;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class App {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("BACKEND_PORT", "8119"));
        String frontendUrl = System.getenv().getOrDefault("FRONTEND_URL", "http://localhost:5173");

        LOGGER.info("Starting Tabula backend on port {}", port);
        LOGGER.info("Frontend origin configured as {}", frontendUrl);
        LOGGER.info("Database host configured as {}", System.getenv().getOrDefault("DB_HOST", "localhost"));

        HikariDataSource dataSource = DatabaseConfig.createDataSource();
        try {
            DatabaseConfig.runMigrations(dataSource);

            Javalin app = Javalin.create(config -> {
                config.http.defaultContentType = "application/json";
                config.showJavalinBanner = false;
            });

            app.before(ctx -> {
                String origin = ctx.header("Origin");
                if (origin != null && origin.startsWith(frontendUrl.replaceAll("/$", ""))) {
                    ctx.header("Access-Control-Allow-Origin", origin);
                    ctx.header("Access-Control-Allow-Credentials", "true");
                    ctx.header("Vary", "Origin");
                }

                if ("OPTIONS".equalsIgnoreCase(ctx.method().name())) {
                    ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
                    ctx.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
                    ctx.status(204).result("");
                }
            });

            app.options("/*", ctx -> {
                ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
                ctx.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
                ctx.status(204);
            });

            PingController.register(app);
            AuthController.register(app, dataSource);

            app.exception(Exception.class, (e, ctx) -> {
                LOGGER.error("Unhandled backend error", e);
                ctx.status(500).json(Map.of("error", "Internal Server Error"));
            });

            app.start(port);
            LOGGER.info("Tabula backend is ready");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Stopping Tabula backend");
                app.stop();
                dataSource.close();
            }));
        } catch (Exception ex) {
            LOGGER.error("Failed to start backend", ex);
            dataSource.close();
            System.exit(1);
        }
    }
}

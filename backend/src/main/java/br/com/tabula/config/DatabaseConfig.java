package br.com.tabula.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseConfig.class);

    private DatabaseConfig() {
    }

    public static HikariDataSource createDataSource() {
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "5432");
        String dbName = System.getenv().getOrDefault("DB_NAME", "eq19");
        String user = System.getenv().getOrDefault("DB_USER", "eq19");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + dbName);
        config.setUsername(user);
        config.setPassword(System.getenv().getOrDefault("DB_PASSWORD", "eq19"));
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setInitializationFailTimeout(0);

        LOGGER.info("Initializing database pool for host {} and database {}", host, dbName);
        return new HikariDataSource(config);
    }

    public static void runMigrations(HikariDataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();
        LOGGER.info("Flyway migrations completed successfully");
    }
}

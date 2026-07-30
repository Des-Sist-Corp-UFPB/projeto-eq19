package br.com.tabula.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class AuditLogPostgreSqlPrivilegesTest {
    private static final String RUNTIME_ROLE = "tabula_audit_runtime_test";
    private static final String RUNTIME_PASSWORD = "runtime_test_password";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("tabula_audit_test")
                    .withUsername("migration_owner")
                    .withPassword("migration_password");

    @Test
    void shouldPreserveActorAndEnforceAppendOnlyPrivilegesWithSeparateRuntimeRole() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        long firstUserId;
        long firstAuditId;
        long secondUserId;
        long secondAuditId;
        try (Connection owner = ownerConnection(); Statement statement = owner.createStatement()) {
            statement.execute("CREATE ROLE " + RUNTIME_ROLE + " LOGIN PASSWORD '" + RUNTIME_PASSWORD + "'");
            statement.execute("GRANT USAGE ON SCHEMA public TO " + RUNTIME_ROLE);
            statement.execute("REVOKE ALL ON audit_logs FROM PUBLIC");
            statement.execute("REVOKE ALL ON audit_logs FROM " + RUNTIME_ROLE);
            statement.execute("GRANT SELECT, INSERT ON audit_logs TO " + RUNTIME_ROLE);
            statement.execute("REVOKE ALL ON SEQUENCE audit_logs_id_seq FROM PUBLIC");
            statement.execute("GRANT USAGE ON SEQUENCE audit_logs_id_seq TO " + RUNTIME_ROLE);

            firstUserId = insertUser(statement, "u_privilege_1", "privilege-1@example.com");
            secondUserId = insertUser(statement, "u_privilege_2", "privilege-2@example.com");
        }

        try (Connection runtime = runtimeConnection(); Statement statement = runtime.createStatement()) {
            firstAuditId = insertAudit(statement, firstUserId, "u_privilege_1");
            secondAuditId = insertAudit(statement, secondUserId, "u_privilege_2");

            assertPermissionDenied(() ->
                    statement.executeUpdate("UPDATE audit_logs SET usuario_id = NULL WHERE id = " + firstAuditId));
            assertPermissionDenied(() ->
                    statement.executeUpdate("UPDATE audit_logs SET sucesso = FALSE WHERE id = " + firstAuditId));
            assertPermissionDenied(() ->
                    statement.executeUpdate("DELETE FROM audit_logs WHERE id = " + firstAuditId));
            assertPermissionDenied(() -> statement.execute("TRUNCATE audit_logs"));
        }

        try (Connection owner = ownerConnection(); Statement statement = owner.createStatement()) {
            SQLException manualNull = assertThrows(SQLException.class, () ->
                    statement.executeUpdate("UPDATE audit_logs SET usuario_id = NULL WHERE id = " + secondAuditId));
            assertEquals("P0001", manualNull.getSQLState());

            statement.executeUpdate("DELETE FROM usuarios WHERE id = " + firstUserId);
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT usuario_id, ator_id_externo FROM audit_logs WHERE id = " + firstAuditId)) {
                assertTrue(resultSet.next());
                assertNull(resultSet.getObject("usuario_id"));
                assertEquals("u_privilege_1", resultSet.getString("ator_id_externo"));
            }
        }

        try (Connection runtime = runtimeConnection(); Statement statement = runtime.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT usuario_id, ator_id_externo FROM audit_logs WHERE id = " + firstAuditId)) {
            assertTrue(resultSet.next());
            assertNull(resultSet.getObject("usuario_id"));
            assertEquals("u_privilege_1", resultSet.getString("ator_id_externo"));
        }
    }

    private static long insertUser(Statement statement, String externalId, String email) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("""
                INSERT INTO usuarios (external_id, nome, email, senha_hash, role, email_verificado)
                VALUES ('%s', 'Privilege Test', '%s', 'hash', 'USER', TRUE)
                RETURNING id
                """.formatted(externalId, email))) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static long insertAudit(Statement statement, long userId, String externalId) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("""
                INSERT INTO audit_logs (
                    usuario_id, ator_id_externo, acao, tipo_recurso, recurso_id, detalhes, sucesso
                )
                VALUES (%d, '%s', 'STATE_UPDATED', 'APP_STATE', '1', '{}'::jsonb, TRUE)
                RETURNING id
                """.formatted(userId, externalId))) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static void assertPermissionDenied(SqlOperation operation) {
        SQLException exception = assertThrows(SQLException.class, operation::execute);
        assertEquals("42501", exception.getSQLState());
    }

    private static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        );
    }

    private static Connection runtimeConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), RUNTIME_ROLE, RUNTIME_PASSWORD);
    }

    @FunctionalInterface
    private interface SqlOperation {
        void execute() throws SQLException;
    }
}

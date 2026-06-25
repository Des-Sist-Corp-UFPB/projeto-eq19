package br.com.tabula.repository;

import br.com.tabula.model.UserAccount;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class UserRepository {
    private final HikariDataSource dataSource;

    public UserRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean emailExists(String email) throws SQLException {
        String sql = "SELECT 1 FROM usuarios WHERE lower(email) = lower(?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public Optional<UserAccount> findByEmail(String email) throws SQLException {
        String sql = """
                SELECT id, COALESCE(external_id, 'u_' || id::text) AS external_id, nome, email, senha_hash, role, email_verificado
                FROM usuarios
                WHERE lower(email) = lower(?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return Optional.empty();
                return Optional.of(mapUser(resultSet));
            }
        }
    }

    public UserAccount createUser(String externalId, String name, String email, String passwordHash, String role) throws SQLException {
        return createUser(externalId, name, email, passwordHash, role, false);
    }

    public UserAccount createUser(String externalId, String name, String email, String passwordHash, String role, boolean emailVerificado) throws SQLException {
        String sql = """
                INSERT INTO usuarios (external_id, nome, email, senha_hash, role, email_verificado)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id, COALESCE(external_id, 'u_' || id::text) AS external_id, nome, email, senha_hash, role, email_verificado
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, externalId);
            statement.setString(2, name);
            statement.setString(3, email);
            statement.setString(4, passwordHash);
            statement.setString(5, role);
            statement.setBoolean(6, emailVerificado);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) return mapUser(resultSet);
            }
        }

        throw new SQLException("Failed to create user in database");
    }

    public void verifyEmail(long userId) throws SQLException {
        String sql = "UPDATE usuarios SET email_verificado = TRUE WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }


    public String createAuthToken(long userId) throws SQLException {
        String token = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString().replace("-", "");
        String sql = """
                INSERT INTO auth_tokens (token, usuario_id, expires_at)
                VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '7 days')
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, token);
            statement.setLong(2, userId);
            statement.executeUpdate();
            return token;
        }
    }

    public void updatePassword(String email, String passwordHash) throws SQLException {
        String sql = "UPDATE usuarios SET senha_hash = ? WHERE lower(email) = lower(?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setString(2, email);
            int updated = statement.executeUpdate();
            if (updated == 0) throw new SQLException("No user updated");
        }
    }

    private static UserAccount mapUser(ResultSet resultSet) throws SQLException {
        return new UserAccount(
                resultSet.getLong("id"),
                resultSet.getString("external_id"),
                resultSet.getString("nome"),
                resultSet.getString("email"),
                resultSet.getString("senha_hash"),
                resultSet.getString("role"),
                resultSet.getBoolean("email_verificado")
        );
    }
}

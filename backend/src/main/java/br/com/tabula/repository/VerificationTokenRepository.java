package br.com.tabula.repository;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public class VerificationTokenRepository {
    private final HikariDataSource dataSource;

    public VerificationTokenRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void createToken(long userId, String token, Instant expiresAt) throws SQLException {
        String sql = """
                INSERT INTO codigos_verificacao (usuario_id, codigo, expiracao, utilizado)
                VALUES (?, ?, ?, FALSE)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, token);
            statement.setTimestamp(3, Timestamp.from(expiresAt));
            statement.executeUpdate();
        }
    }

    public Optional<TokenInfo> findToken(String token) throws SQLException {
        String sql = """
                SELECT usuario_id, expiracao, utilizado
                FROM codigos_verificacao
                WHERE codigo = ? AND utilizado = FALSE
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, token);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    long userId = resultSet.getLong("usuario_id");
                    Timestamp expiracao = resultSet.getTimestamp("expiracao");
                    return Optional.of(new TokenInfo(userId, expiracao.toInstant()));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<TokenInfo> findCodeForEmail(String email, String code) throws SQLException {
        String sql = """
                SELECT cv.usuario_id, cv.expiracao, cv.utilizado
                FROM codigos_verificacao cv
                INNER JOIN usuarios u ON u.id = cv.usuario_id
                WHERE LOWER(u.email) = LOWER(?)
                AND cv.codigo = ?
                AND cv.utilizado = FALSE
                ORDER BY cv.criado_em DESC
                LIMIT 1
                """;

        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            statement.setString(2, code);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    long userId = resultSet.getLong("usuario_id");
                    Timestamp expiracao = resultSet.getTimestamp("expiracao");
                    return Optional.of(new TokenInfo(userId, expiracao.toInstant()));
                }
            }
        }

        return Optional.empty();
    }    

    public void deleteToken(String token) throws SQLException {
        String sql = "DELETE FROM codigos_verificacao WHERE codigo = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, token);
            statement.executeUpdate();
        }
    }

    public void deleteTokensByUser(long userId) throws SQLException {
        String sql = "DELETE FROM codigos_verificacao WHERE usuario_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

    public static class TokenInfo {
        private final long userId;
        private final Instant expiresAt;

        public TokenInfo(long userId, Instant expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }

        public long getUserId() { return userId; }
        public Instant getExpiresAt() { return expiresAt; }
    }
}

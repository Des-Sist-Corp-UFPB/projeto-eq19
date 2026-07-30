package br.com.tabula.service;

import br.com.tabula.model.AuthenticatedPrincipal;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AuthenticatedUserService {
    private final HikariDataSource dataSource;

    public AuthenticatedUserService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<AuthenticatedPrincipal> resolve(String authorization) throws SQLException {
        String token = extractBearer(authorization);
        if (token == null) return Optional.empty();
        try (Connection connection = dataSource.getConnection()) {
            return resolveToken(connection, token);
        }
    }

    public Optional<AuthenticatedPrincipal> resolve(Connection connection, String authorization) throws SQLException {
        String token = extractBearer(authorization);
        if (token == null) return Optional.empty();
        return resolveToken(connection, token);
    }

    private static Optional<AuthenticatedPrincipal> resolveToken(Connection connection, String token) throws SQLException {
        String sql = """
                SELECT u.id, u.external_id, u.role
                FROM auth_tokens t
                JOIN usuarios u ON u.id = t.usuario_id
                WHERE t.token = ? AND t.expires_at > CURRENT_TIMESTAMP
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, token);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return Optional.empty();
                return Optional.of(new AuthenticatedPrincipal(
                        resultSet.getLong("id"),
                        resultSet.getString("external_id"),
                        resultSet.getString("role")
                ));
            }
        }
    }

    private static String extractBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String token = authorization.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }
}

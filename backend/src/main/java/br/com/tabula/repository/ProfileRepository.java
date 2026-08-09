package br.com.tabula.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Optional;

public final class ProfileRepository {
    public Optional<ProfileData> findByUserId(Connection connection, long userId, boolean lock) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, external_id, nome, email, role, curso, bio, avatar_url, criado_em
                FROM usuarios WHERE id = ?
                """ + (lock ? " FOR UPDATE" : ""))) {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    public ProfileData update(Connection connection, long userId, String name, String course,
                              String bio, String avatarUrl) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE usuarios SET nome=?, curso=?, bio=?, avatar_url=?, atualizado_em=CURRENT_TIMESTAMP
                WHERE id=?
                """)) {
            statement.setString(1, name); setNullable(statement, 2, course);
            setNullable(statement, 3, bio); setNullable(statement, 4, avatarUrl);
            statement.setLong(5, userId);
            if (statement.executeUpdate() != 1) throw new SQLException("profile_update_conflict");
        }
        return findByUserId(connection, userId, false).orElseThrow();
    }

    private static void setNullable(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR); else statement.setString(index, value);
    }

    private static ProfileData map(ResultSet rows) throws SQLException {
        return new ProfileData(rows.getLong("id"), rows.getString("external_id"), rows.getString("nome"),
                rows.getString("email"), rows.getString("role"), rows.getString("curso"),
                rows.getString("bio"), rows.getString("avatar_url"),
                rows.getTimestamp("criado_em").toLocalDateTime());
    }

    public record ProfileData(long databaseId, String externalId, String name, String email, String role,
                              String course, String bio, String avatarUrl, LocalDateTime joinedAt) {}
}

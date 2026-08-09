package br.com.tabula.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class FavoriteRepository {
    public List<String> findGameIds(Connection connection, long userId) throws SQLException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT j.external_id FROM favoritos f
                JOIN jogos j ON j.id = f.jogo_id
                WHERE f.usuario_id = ? ORDER BY f.criado_em, j.external_id
                """)) {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(rows.getString(1));
            }
        }
        return result;
    }

    public long requireGameId(Connection connection, String externalId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM jogos WHERE external_id = ?")) {
            statement.setString(1, externalId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("game_not_found");
                return rows.getLong(1);
            }
        }
    }

    public boolean insert(Connection connection, long userId, long gameId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO favoritos (usuario_id, jogo_id, criado_em) VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (usuario_id, jogo_id) DO NOTHING
                """)) {
            statement.setLong(1, userId); statement.setLong(2, gameId);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean delete(Connection connection, long userId, long gameId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM favoritos WHERE usuario_id = ? AND jogo_id = ?")) {
            statement.setLong(1, userId); statement.setLong(2, gameId);
            return statement.executeUpdate() == 1;
        }
    }
}

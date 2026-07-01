package br.com.tabula.repository;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.zaxxer.hikari.HikariDataSource;

class VerificationTokenRepositoryTest {

    @Test
    void shouldInsertTokenWithExpectedParameters() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        VerificationTokenRepository repository = new VerificationTokenRepository(dataSource);
        Instant expiresAt = Instant.parse("2026-07-01T12:00:00Z");

        repository.createToken(42L, "token-123", expiresAt);

        verify(statement).setLong(1, 42L);
        verify(statement).setString(2, "token-123");
    }

    @Test
    void shouldReturnTokenInfoWhenMatchingTokenExists() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        Instant expiresAt = Instant.parse("2026-07-01T12:00:00Z");

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("usuario_id")).thenReturn(9L);
        when(resultSet.getTimestamp("expiracao")).thenReturn(Timestamp.from(expiresAt));

        VerificationTokenRepository repository = new VerificationTokenRepository(dataSource);
        Optional<VerificationTokenRepository.TokenInfo> tokenInfo = repository.findToken("token-123");

        assertTrue(tokenInfo.isPresent());
        assertEquals(9L, tokenInfo.get().getUserId());
        assertEquals(expiresAt, tokenInfo.get().getExpiresAt());
    }

    @Test
    void shouldReturnEmptyOptionalWhenTokenDoesNotExist() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        VerificationTokenRepository repository = new VerificationTokenRepository(dataSource);
        Optional<VerificationTokenRepository.TokenInfo> tokenInfo = repository.findToken("missing-token");

        assertFalse(tokenInfo.isPresent());
    }

    @Test
    void shouldReturnTokenInfoWhenMatchingCodeForEmailExists() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        Instant expiresAt = Instant.parse("2026-07-01T12:00:00Z");

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("usuario_id")).thenReturn(21L);
        when(resultSet.getTimestamp("expiracao")).thenReturn(Timestamp.from(expiresAt));

        VerificationTokenRepository repository = new VerificationTokenRepository(dataSource);
        Optional<VerificationTokenRepository.TokenInfo> tokenInfo = repository.findCodeForEmail("user@example.com", "123456");

        assertTrue(tokenInfo.isPresent());
        assertEquals(21L, tokenInfo.get().getUserId());
        assertEquals(expiresAt, tokenInfo.get().getExpiresAt());
    }

    @Test
    void shouldDeleteTokensByUserAndByTokenValue() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement deleteTokenStatement = mock(PreparedStatement.class);
        PreparedStatement deleteTokensByUserStatement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(deleteTokenStatement, deleteTokensByUserStatement);
        when(deleteTokenStatement.executeUpdate()).thenReturn(1);
        when(deleteTokensByUserStatement.executeUpdate()).thenReturn(1);

        VerificationTokenRepository repository = new VerificationTokenRepository(dataSource);
        repository.deleteToken("token");
        repository.deleteTokensByUser(7L);

        verify(deleteTokenStatement).setString(1, "token");
        verify(deleteTokensByUserStatement).setLong(1, 7L);
    }

    @Test
    void shouldReturnEmptyOptionalWhenNoMatchingCodeIsFound() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        VerificationTokenRepository repository = new VerificationTokenRepository(dataSource);
        Optional<VerificationTokenRepository.TokenInfo> tokenInfo = repository.findCodeForEmail("user@example.com", "code");

        assertFalse(tokenInfo.isPresent());
    }
}

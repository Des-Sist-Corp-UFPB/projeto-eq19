package br.com.tabula.repository;

import br.com.tabula.model.UserAccount;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRepositoryTest {

    @Test
    void shouldReportWhenEmailAlreadyExists() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);

        UserRepository repository = new UserRepository(dataSource);

        assertTrue(repository.emailExists("user@example.com"));
        verify(statement).setString(1, "user@example.com");
    }

    @Test
    void shouldMapUserFromResultSetWhenFindingByEmail() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("id")).thenReturn(7L);
        when(resultSet.getString("external_id")).thenReturn("u_7");
        when(resultSet.getString("nome")).thenReturn("Alice");
        when(resultSet.getString("email")).thenReturn("alice@example.com");
        when(resultSet.getString("senha_hash")).thenReturn("hash");
        when(resultSet.getString("role")).thenReturn("PLAYER");
        when(resultSet.getBoolean("email_verificado")).thenReturn(true);

        UserRepository repository = new UserRepository(dataSource);
        Optional<UserAccount> user = repository.findByEmail("alice@example.com");

        assertTrue(user.isPresent());
        assertEquals(7L, user.get().getId());
        assertEquals("u_7", user.get().getExternalId());
        assertEquals("Alice", user.get().getName());
        assertTrue(user.get().isEmailVerificado());
    }

    @Test
    void shouldCreateUserAndMapReturnedValues() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("id")).thenReturn(11L);
        when(resultSet.getString("external_id")).thenReturn("u_11");
        when(resultSet.getString("nome")).thenReturn("Bob");
        when(resultSet.getString("email")).thenReturn("bob@example.com");
        when(resultSet.getString("senha_hash")).thenReturn("hash");
        when(resultSet.getString("role")).thenReturn("ADMIN");
        when(resultSet.getBoolean("email_verificado")).thenReturn(false);

        UserRepository repository = new UserRepository(dataSource);
        UserAccount user = repository.createUser("u_11", "Bob", "bob@example.com", "hash", "ADMIN");

        assertEquals("Bob", user.getName());
        assertEquals("ADMIN", user.getRole());
        assertFalse(user.isEmailVerificado());
    }

    @Test
    void shouldReturnEmptyOptionalWhenUserIsNotFoundByEmail() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        UserRepository repository = new UserRepository(dataSource);

        assertFalse(repository.findByEmail("missing@example.com").isPresent());
    }

    @Test
    void shouldCreateAuthTokenAndReturnIt() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        UserRepository repository = new UserRepository(dataSource);
        String token = repository.createAuthToken(5L);

        assertTrue(token != null && token.length() > 20);
        verify(statement).setString(1, token);
        verify(statement).setLong(2, 5L);
    }

    @Test
    void shouldVerifyEmailByUpdatingUserStatus() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        UserRepository repository = new UserRepository(dataSource);
        repository.verifyEmail(9L);

        verify(statement).setLong(1, 9L);
    }

    @Test
    void shouldThrowWhenUpdatingPasswordDoesNotAffectAnyRow() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);

        UserRepository repository = new UserRepository(dataSource);

        SQLException exception = assertThrows(SQLException.class,
                () -> repository.updatePassword("user@example.com", "new-hash"));

        assertTrue(exception.getMessage().contains("No user updated"));
    }

    @Test
    void shouldThrowSqlExceptionWhenCreateUserReturnsNoRows() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        UserRepository repository = new UserRepository(dataSource);

        SQLException exception = assertThrows(SQLException.class,
                () -> repository.createUser("u_fail", "Bob", "bob@example.com", "hash", "USER"));

        assertTrue(exception.getMessage().contains("Failed to create user in database"));
    }

    @Test
    void shouldSuccessfullyUpdatePassword() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        UserRepository repository = new UserRepository(dataSource);
        repository.updatePassword("user@example.com", "new-hash");

        verify(statement).setString(1, "new-hash");
        verify(statement).setString(2, "user@example.com");
    }
}

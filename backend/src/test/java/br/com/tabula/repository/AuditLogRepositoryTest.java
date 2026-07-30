package br.com.tabula.repository;

import br.com.tabula.dto.AuditLogFilter;
import br.com.tabula.model.AuditLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogRepositoryTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldInsertTheOfficialAuditRecord() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        AuditLogRepository repository = new AuditLogRepository(dataSource);
        AuditLog log = new AuditLog(
                null, 9L, "u_9", "LOGIN_SUCCEEDED", "USER", "u_9",
                MAPPER.readTree("{\"reason\":\"accepted\"}"),
                "127.0.0.1", "agent", true, null, null
        );

        repository.insert(connection, log);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("INSERT INTO audit_logs"));
        verify(statement).setLong(1, 9L);
        verify(statement).setString(2, "u_9");
        verify(statement).setString(3, "LOGIN_SUCCEEDED");
        verify(statement).executeUpdate();
    }

    @Test
    void shouldFilterPageAndKeepStableNewestFirstOrdering() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("id")).thenReturn(42L);
        when(resultSet.getLong("usuario_id")).thenReturn(9L);
        when(resultSet.wasNull()).thenReturn(false);
        when(resultSet.getString("ator_id_externo")).thenReturn("u_9");
        when(resultSet.getString("acao")).thenReturn("STATE_UPDATED");
        when(resultSet.getString("tipo_recurso")).thenReturn("APP_STATE");
        when(resultSet.getString("recurso_id")).thenReturn("1");
        when(resultSet.getString("detalhes")).thenReturn("{\"changedSections\":[\"users\"]}");
        when(resultSet.getString("endereco_ip")).thenReturn("127.0.0.1");
        when(resultSet.getString("user_agent")).thenReturn("agent");
        when(resultSet.getBoolean("sucesso")).thenReturn(true);
        when(resultSet.getString("trace_id")).thenReturn(null);
        when(resultSet.getTimestamp("criado_em")).thenReturn(Timestamp.from(Instant.parse("2026-01-02T03:04:05Z")));
        AuditLogRepository repository = new AuditLogRepository(dataSource);
        AuditLogFilter filter = new AuditLogFilter(
                2, 25, "STATE_UPDATED", "u_9", "APP_STATE", "1",
                true, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-31T23:59:59Z")
        );

        List<AuditLog> page = repository.findPage(filter);

        assertEquals(1, page.size());
        assertEquals(42L, page.get(0).getId());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("acao = ?"));
        assertTrue(sql.getValue().contains("ator_id_externo = ?"));
        assertTrue(sql.getValue().contains("ORDER BY criado_em DESC, id DESC"));
        verify(statement).setInt(8, 25);
        verify(statement).setInt(9, 25);
    }
}

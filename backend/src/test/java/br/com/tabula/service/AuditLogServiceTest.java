package br.com.tabula.service;

import br.com.tabula.model.AuditAction;
import br.com.tabula.model.AuditLog;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.AuditLogRepository;
import com.zaxxer.hikari.HikariDataSource;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogServiceTest {

    @Test
    void shouldRecordAuthenticatedActorAndOnlyAllowlistedDetails() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        AuditLogRepository repository = mock(AuditLogRepository.class);
        Connection connection = mock(Connection.class);
        AuditLogService service = new AuditLogService(dataSource, repository);
        AuthenticatedPrincipal actor = new AuthenticatedPrincipal(17L, "u_17", "ADMIN");

        service.record(
                connection,
                actor,
                AuditAction.STATE_UPDATED,
                "APP_STATE",
                "1",
                true,
                "203.0.113.8",
                "test-agent",
                Map.of(
                        "changedSections", List.of("users", "users", "boardGames", "logs", "password"),
                        "initialization", false,
                        "reason", "accepted",
                        "body", "{\"password\":\"secret\"}",
                        "authorization", "Bearer secret"
                )
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).insert(org.mockito.ArgumentMatchers.eq(connection), captor.capture());
        AuditLog log = captor.getValue();
        assertEquals(17L, log.getUserDatabaseId());
        assertEquals("u_17", log.getActorExternalId());
        assertEquals("STATE_UPDATED", log.getAction());
        assertEquals("203.0.113.8", log.getIpAddress());
        assertEquals(2, log.getDetails().get("changedSections").size());
        assertEquals("users", log.getDetails().get("changedSections").get(0).asText());
        assertEquals("boardGames", log.getDetails().get("changedSections").get(1).asText());
        assertFalse(log.getDetails().has("body"));
        assertFalse(log.getDetails().has("authorization"));
        assertFalse(log.getDetails().toString().contains("secret"));
    }

    @Test
    void shouldNeverStoreRequestBodyOrUntrustedForwardingValue() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        AuditLogRepository repository = mock(AuditLogRepository.class);
        Connection connection = mock(Connection.class);
        AuditLogService service = new AuditLogService(dataSource, repository);

        service.record(
                connection,
                null,
                AuditAction.LOGIN_REJECTED,
                "USER",
                null,
                false,
                "198.51.100.2, 10.0.0.1",
                "agent",
                Map.of(
                        "reason", "invalid_credentials",
                        "request", Map.of("email", "person@example.com", "password", "secret"),
                        "headers", Map.of("X-Forwarded-For", "198.51.100.2")
                )
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).insert(org.mockito.ArgumentMatchers.eq(connection), captor.capture());
        AuditLog log = captor.getValue();
        assertNull(log.getIpAddress());
        assertEquals("{\"reason\":\"invalid_credentials\"}", log.getDetails().toString());
    }

    @Test
    void shouldUseOnlyAValidCurrentTraceId() {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogService service = new AuditLogService(dataSource, repository);

        AuditLog withoutSpan = service.build(
                null, AuditAction.LOGIN_REJECTED, "USER", null,
                false, null, null, Map.of()
        );
        assertNull(withoutSpan.getTraceId());

        String traceId = "0123456789abcdef0123456789abcdef";
        SpanContext spanContext = SpanContext.create(
                traceId,
                "0123456789abcdef",
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );
        try (Scope ignored = Span.wrap(spanContext).makeCurrent()) {
            AuditLog withSpan = service.build(
                    null, AuditAction.LOGIN_SUCCEEDED, "USER", "u_1",
                    true, null, null, Map.of()
            );
            assertEquals(traceId, withSpan.getTraceId());
        }
    }

    @Test
    void shouldReturnFalseWhenBestEffortPersistenceFails() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        AuditLogRepository repository = mock(AuditLogRepository.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        doThrow(new SQLException("audit unavailable"))
                .when(repository).insert(org.mockito.ArgumentMatchers.eq(connection), org.mockito.ArgumentMatchers.any());
        AuditLogService service = new AuditLogService(dataSource, repository);

        boolean recorded = service.recordBestEffort(
                null, AuditAction.LOGIN_REJECTED, "USER", null,
                false, null, null, Map.of("reason", "invalid_credentials")
        );

        assertFalse(recorded);
    }
}

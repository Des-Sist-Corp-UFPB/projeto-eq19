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

    @Test
    void shouldAllowOnlySafeAiAuditMetadata() {
        AuditLogService service = new AuditLogService(mock(HikariDataSource.class), mock(AuditLogRepository.class));
        AuditLog log = service.build(null, AuditAction.AI_EVENT_DRAFT_REJECTED, "AI_EVENT_DRAFT", null,
                false, null, null, Map.ofEntries(
                        Map.entry("model", "gpt-4o-mini"), Map.entry("promptLength", 42),
                        Map.entry("warningCount", 1), Map.entry("durationMs", 123),
                        Map.entry("failureReason", "provider_failure"), Map.entry("failureCategory", "timeout"),
                        Map.entry("promptTokens", 75), Map.entry("completionTokens", 120),
                        Map.entry("totalTokens", 195), Map.entry("prompt", "segredo"),
                        Map.entry("authorization", "Bearer token")
                ));
        assertEquals(123, log.getDetails().get("durationMs").asInt());
        assertEquals("timeout", log.getDetails().get("failureCategory").asText());
        assertEquals(75, log.getDetails().get("promptTokens").asInt());
        assertEquals(120, log.getDetails().get("completionTokens").asInt());
        assertEquals(195, log.getDetails().get("totalTokens").asInt());
        assertFalse(log.getDetails().has("prompt"));
        assertFalse(log.getDetails().has("authorization"));
    }

    @Test
    void shouldStoreStructuredValidationMetadataWithoutSensitiveValues() {
        AuditLogService service = new AuditLogService(
                mock(HikariDataSource.class), mock(AuditLogRepository.class));
        AuditLog log = service.build(null, AuditAction.STATE_UPDATE_REJECTED, "APP_STATE", "1",
                false, null, null, Map.ofEntries(
                        Map.entry("reason", "invalid_payload"),
                        Map.entry("reasonCode", "unknown_field"),
                        Map.entry("section", "boardGames"),
                        Map.entry("resourceId", "g1"),
                        Map.entry("field", "tags"),
                        Map.entry("detail", "field is not supported"),
                        Map.entry("passwordHash", "hash-secret"),
                        Map.entry("token", "token-secret"),
                        Map.entry("authorization", "Bearer secret"),
                        Map.entry("body", "{\"password\":\"secret\"}")
                ));

        assertEquals("unknown_field", log.getDetails().path("reasonCode").asText());
        assertEquals("boardGames", log.getDetails().path("section").asText());
        assertEquals("g1", log.getDetails().path("resourceId").asText());
        assertEquals("tags", log.getDetails().path("field").asText());
        assertFalse(log.getDetails().toString().contains("secret"));
        assertFalse(log.getDetails().has("passwordHash"));
        assertFalse(log.getDetails().has("token"));
        assertFalse(log.getDetails().has("authorization"));
        assertFalse(log.getDetails().has("body"));
    }
}

package br.com.tabula.service;

import br.com.tabula.model.AuditAction;
import br.com.tabula.model.AuditLog;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariDataSource;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class AuditLogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_CHANGED_SECTIONS =
            Set.of("users", "boardGames", "sessions", "events");

    private final HikariDataSource dataSource;
    private final AuditLogRepository repository;

    public AuditLogService(HikariDataSource dataSource) {
        this(dataSource, new AuditLogRepository(dataSource));
    }

    public AuditLogService(HikariDataSource dataSource, AuditLogRepository repository) {
        this.dataSource = dataSource;
        this.repository = repository;
    }

    public void record(
            Connection connection,
            AuthenticatedPrincipal actor,
            AuditAction action,
            String resourceType,
            String resourceId,
            boolean success,
            String ipAddress,
            String userAgent,
            Map<String, ?> details) throws SQLException {
        repository.insert(connection, build(
                actor, action, resourceType, resourceId, success, ipAddress, userAgent, details
        ));
        LOGGER.atInfo()
                .addKeyValue("audit_action", action.name())
                .addKeyValue("resource_type", resourceType)
                .addKeyValue("resource_id", resourceId)
                .addKeyValue("success", success)
                .log("Audit event recorded");
    }

    public boolean recordBestEffort(
            AuthenticatedPrincipal actor,
            AuditAction action,
            String resourceType,
            String resourceId,
            boolean success,
            String ipAddress,
            String userAgent,
            Map<String, ?> details) {
        try (Connection connection = dataSource.getConnection()) {
            record(connection, actor, action, resourceType, resourceId, success, ipAddress, userAgent, details);
            return true;
        } catch (Exception ex) {
            LOGGER.atError()
                    .addKeyValue("audit_action", action.name())
                    .addKeyValue("resource_type", resourceType)
                    .addKeyValue("resource_id", resourceId)
                    .addKeyValue("success", success)
                    .log("Failed to persist audit event");
            return false;
        }
    }

    AuditLog build(
            AuthenticatedPrincipal actor,
            AuditAction action,
            String resourceType,
            String resourceId,
            boolean success,
            String ipAddress,
            String userAgent,
            Map<String, ?> details) {
        return new AuditLog(
                null,
                actor == null ? null : actor.getDatabaseId(),
                actor == null ? null : actor.getExternalId(),
                action.name(),
                safeText(resourceType, 80),
                safeText(resourceId, 120),
                sanitizeDetails(details),
                normalizeIp(ipAddress),
                safeText(userAgent, 512),
                success,
                currentTraceId(),
                null
        );
    }

    private static JsonNode sanitizeDetails(Map<String, ?> details) {
        ObjectNode sanitized = MAPPER.createObjectNode();
        if (details == null) return sanitized;

        Object reason = details.get("reason");
        if (reason instanceof String text && !text.isBlank()) {
            sanitized.put("reason", safeText(text, 80));
        }

        Object initialization = details.get("initialization");
        if (initialization instanceof Boolean bool) {
            sanitized.put("initialization", bool);
        }

        copySafeString(sanitized, details, "model", 80);
        copySafeString(sanitized, details, "resultGameId", 80);
        copySafeString(sanitized, details, "failureReason", 80);
        copySafeString(sanitized, details, "failureCategory", 80);
        copySafeInteger(sanitized, details, "promptLength");
        copySafeInteger(sanitized, details, "warningCount");
        copySafeInteger(sanitized, details, "durationMs");
        Object success = details.get("success");
        if (success instanceof Boolean bool) sanitized.put("success", bool);

        Object changedSections = details.get("changedSections");
        if (changedSections instanceof Collection<?> values) {
            ArrayNode array = sanitized.putArray("changedSections");
            values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(ALLOWED_CHANGED_SECTIONS::contains)
                    .distinct()
                    .forEach(array::add);
        }
        return sanitized;
    }

    private static void copySafeString(ObjectNode target, Map<String, ?> source, String key, int limit) {
        Object value = source.get(key);
        if (value instanceof String text) {
            String safe = safeText(text, limit);
            if (safe != null) target.put(key, safe);
        }
    }

    private static void copySafeInteger(ObjectNode target, Map<String, ?> source, String key) {
        Object value = source.get(key);
        if (value instanceof Number number) target.put(key, Math.max(0, number.intValue()));
    }

    private static String currentTraceId() {
        SpanContext spanContext = Span.current().getSpanContext();
        return spanContext.isValid() ? spanContext.getTraceId() : null;
    }

    private static String normalizeIp(String candidate) {
        if (candidate == null || candidate.isBlank()) return null;
        String value = candidate.trim();
        boolean ipv4Shape = value.matches("\\d{1,3}(\\.\\d{1,3}){3}");
        boolean ipv6Shape = value.contains(":") && value.matches("[0-9a-fA-F:]{2,45}");
        if (!ipv4Shape && !ipv6Shape) return null;
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safeText(String value, int maximumLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= maximumLength ? trimmed : trimmed.substring(0, maximumLength);
    }
}

package br.com.tabula.controller;

import br.com.tabula.model.AuditAction;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.service.AuditLogService;
import br.com.tabula.service.AuthenticatedUserService;
import br.com.tabula.service.StateAuthorizationService;
import br.com.tabula.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StateController {
    private static final Logger LOGGER = LoggerFactory.getLogger(StateController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StateController() {
    }

    private static boolean isRelationalReadEnabled() {
        String value = System.getenv("RELATIONAL_STATE_READ_ENABLED");
        if (value == null || value.isBlank()) {
            value = System.getProperty("RELATIONAL_STATE_READ_ENABLED");
        }
        return "true".equalsIgnoreCase(value);
    }

    private static boolean isRelationalReadGuardEnabled() {
        String value = System.getenv("RELATIONAL_STATE_READ_GUARD_ENABLED");
        if (value == null || value.isBlank()) {
            value = System.getProperty("RELATIONAL_STATE_READ_GUARD_ENABLED");
        }
        return "true".equalsIgnoreCase(value);
    }

    private static boolean isRelationalComparisonEnabled() {
        String value = System.getenv("RELATIONAL_STATE_COMPARISON_ENABLED");
        if (value == null || value.isBlank()) {
            value = System.getProperty("RELATIONAL_STATE_COMPARISON_ENABLED");
        }
        return "true".equalsIgnoreCase(value);
    }

    private static boolean isRelationalBackfillEnabled() {
        String value = System.getenv("RELATIONAL_STATE_BACKFILL_ENABLED");
        if (value == null || value.isBlank()) {
            value = System.getProperty("RELATIONAL_STATE_BACKFILL_ENABLED");
        }
        return "true".equalsIgnoreCase(value);
    }

    public static void register(Javalin app, HikariDataSource dataSource) {
        register(app, dataSource, source -> new RelationalSlices(
                relationalEvents(source), relationalSessions(source)));
    }

    static void register(Javalin app, HikariDataSource dataSource, RelationalSliceReader relationalSliceReader) {
        AuditLogService auditLogService = new AuditLogService(dataSource);
        AuthenticatedUserService authenticatedUserService = new AuthenticatedUserService(dataSource);
        StateAuthorizationService stateAuthorizationService = new StateAuthorizationService();
        app.post("/state/relational-backfill", ctx -> {
            if (!isRelationalBackfillEnabled()) {
                ctx.status(404).json(Map.of("error", "Not Found"));
                return;
            }

            String legacyJson = null;
            try {
                legacyJson = readState(dataSource);
            } catch (SQLException ex) {
                LOGGER.atError()
                      .addKeyValue("state_id", 1)
                      .addKeyValue("operation", "relational_backfill")
                      .setCause(ex)
                      .log("Failed to read legacy state for backfill");
                ctx.status(500).json(Map.of(
                    "ok", false,
                    "message", "Relational backfill failed",
                    "errors", java.util.List.of("Failed to read legacy state: " + ex.getMessage())
                ));
                return;
            }

            if (legacyJson == null || legacyJson.isBlank()) {
                ctx.status(500).json(Map.of(
                    "ok", false,
                    "message", "Relational backfill failed",
                    "errors", java.util.List.of("Legacy state is empty or not initialized.")
                ));
                return;
            }

            try {
                br.com.tabula.service.RelationalStateSyncService.syncFromStateJson(dataSource, legacyJson, true);
            } catch (Exception ex) {
                LOGGER.atError()
                      .addKeyValue("state_id", 1)
                      .addKeyValue("operation", "relational_backfill")
                      .setCause(ex)
                      .log("Failed to sync legacy state during backfill");
                ctx.status(500).json(Map.of(
                    "ok", false,
                    "message", "Relational backfill failed",
                    "errors", java.util.List.of(ex.getMessage() != null ? ex.getMessage() : ex.toString())
                ));
                return;
            }

            String relationalJson = null;
            try {
                relationalJson = br.com.tabula.service.RelationalStateReadService.readStateAsJson(dataSource);
            } catch (Exception ex) {
                LOGGER.atError()
                      .addKeyValue("state_id", 1)
                      .addKeyValue("operation", "relational_backfill")
                      .setCause(ex)
                      .log("Failed to read relational state after backfill");
                ctx.status(500).json(Map.of(
                    "ok", false,
                    "message", "Relational backfill failed",
                    "errors", java.util.List.of("Failed to read relational state after sync: " + ex.getMessage())
                ));
                return;
            }

            String comparisonReport = br.com.tabula.service.RelationalStateComparisonService.compareStateJson(legacyJson, relationalJson);

            com.fasterxml.jackson.databind.node.ObjectNode responseNode = MAPPER.createObjectNode();
            
            try {
                com.fasterxml.jackson.databind.JsonNode comparisonNode = MAPPER.readTree(comparisonReport);
                boolean comparisonOk = comparisonNode.has("ok") && comparisonNode.get("ok").asBoolean(false);
                responseNode.put("ok", comparisonOk);
                responseNode.put("message", comparisonOk ? "Relational backfill completed" : "Relational backfill completed with validation errors");
                responseNode.set("comparison", comparisonNode);
            } catch (Exception ex) {
                LOGGER.atError()
                      .addKeyValue("state_id", 1)
                      .addKeyValue("operation", "relational_backfill")
                      .setCause(ex)
                      .log("Failed to parse comparison report during backfill");
                responseNode.put("ok", false);
                responseNode.put("message", "Relational backfill completed with validation errors");
                responseNode.put("comparison", comparisonReport);
            }

            ctx.contentType("application/json").result(responseNode.toPrettyString());
        });

        app.get("/state/relational-comparison", ctx -> {
            if (!isRelationalComparisonEnabled()) {
                ctx.status(404).json(Map.of("error", "Not Found"));
                return;
            }

            String legacyJson = null;
            try {
                legacyJson = readState(dataSource);
            } catch (SQLException ex) {
                LOGGER.atError()
                      .addKeyValue("state_id", 1)
                      .addKeyValue("operation", "relational_comparison")
                      .setCause(ex)
                      .log("Failed to read legacy state for comparison");
                ctx.status(500).json(Map.of("error", "Erro ao carregar estado legado."));
                return;
            }

            if (legacyJson == null) {
                legacyJson = "{}";
            }

            String relationalJson = null;
            String readError = null;
            try {
                relationalJson = br.com.tabula.service.RelationalStateReadService.readStateAsJson(dataSource);
            } catch (Exception ex) {
                LOGGER.atError()
                      .addKeyValue("state_id", 1)
                      .addKeyValue("operation", "relational_comparison")
                      .setCause(ex)
                      .log("Failed to read relational state for comparison");
                readError = ex.getMessage();
            }

            if (readError != null) {
                com.fasterxml.jackson.databind.node.ObjectNode report = MAPPER.createObjectNode();
                report.put("ok", false);
                report.putObject("summary");
                report.putArray("warnings");
                com.fasterxml.jackson.databind.node.ArrayNode errors = report.putArray("errors");
                errors.add("Relational read failed: " + readError);
                ctx.contentType("application/json").result(report.toPrettyString());
                return;
            }

            String reportJson = br.com.tabula.service.RelationalStateComparisonService.compareStateJson(legacyJson, relationalJson);
            ctx.contentType("application/json").result(reportJson);
        });

        app.get("/state", ctx -> {
            try {
                String payload = null;
                boolean relationalPayload = false;
                if (isRelationalReadEnabled()) {
                    if (isRelationalReadGuardEnabled()) {
                        String legacyJson = null;
                        try {
                            legacyJson = readState(dataSource);
                        } catch (SQLException ex) {
                            LOGGER.atError()
                                  .addKeyValue("state_id", 1)
                                  .addKeyValue("operation", "state_read")
                                  .setCause(ex)
                                  .log("Failed to read legacy state for guard check");
                        }

                        if (legacyJson != null && !legacyJson.isBlank()) {
                            try {
                                String relationalJson = br.com.tabula.service.RelationalStateReadService.readStateAsJson(dataSource);
                                String comparisonReport = br.com.tabula.service.RelationalStateComparisonService.compareStateJson(legacyJson, relationalJson);
                                
                                com.fasterxml.jackson.databind.JsonNode comparisonNode = MAPPER.readTree(comparisonReport);
                                boolean comparisonOk = comparisonNode.has("ok") && comparisonNode.get("ok").asBoolean(false);

                                if (comparisonOk) {
                                    payload = relationalJson;
                                    relationalPayload = true;
                                } else {
                                    LOGGER.atWarn()
                                          .addKeyValue("state_id", 1)
                                          .addKeyValue("operation", "state_read")
                                          .log("Relational read guard check failed. Comparison report: {}", comparisonReport);
                                    payload = legacyJson;
                                }
                            } catch (Exception ex) {
                                LOGGER.atError()
                                      .addKeyValue("state_id", 1)
                                      .addKeyValue("operation", "state_read")
                                      .setCause(ex)
                                      .log("Failed during relational read guard check, falling back to legacy state");
                                payload = legacyJson;
                            }
                        }
                    } else {
                        try {
                            payload = br.com.tabula.service.RelationalStateReadService.readStateAsJson(dataSource);
                            relationalPayload = true;
                        } catch (Exception ex) {
                            LOGGER.atError()
                                  .addKeyValue("state_id", 1)
                                  .addKeyValue("operation", "state_read")
                                  .setCause(ex)
                                  .log("Failed to read relational database state, falling back to app_state");
                        }
                    }
                }

                if (payload == null || payload.isBlank()) {
                    payload = readState(dataSource);
                }

                if (payload == null || payload.isBlank()) {
                    ctx.status(404).json(Map.of("error", "Estado ainda não inicializado."));
                    return;
                }
                if (!relationalPayload) payload = overlayRelationalSlices(dataSource, payload);
                ctx.contentType("application/json").result(payload);
            } catch (SQLException ex) {
                LOGGER.atError()
                      .addKeyValue("state_id", 1)
                      .addKeyValue("operation", "state_read")
                      .setCause(ex)
                      .log("Failed to read app state");
                ctx.status(500).json(Map.of("error", "Não foi possível carregar os dados."));
            }
        });

        app.put("/state", ctx -> {
            String payload = ctx.body();
            if (payload == null || payload.isBlank()) {
                auditLogService.recordBestEffort(
                        null, AuditAction.STATE_UPDATE_REJECTED, "APP_STATE", "1", false,
                        ctx.ip(), ctx.header("User-Agent"), Map.of("reason", "invalid_payload")
                );
                ctx.status(400).json(Map.of("error", "Payload vazio."));
                return;
            }

            try {
                StateSnapshot snapshot = readStateSnapshot(dataSource);
                Optional<AuthenticatedPrincipal> principal = snapshot.exists()
                        ? authenticatedUserService.resolve(ctx.header("Authorization"))
                        : Optional.empty();
                if (snapshot.exists() && principal.isEmpty()) {
                    auditLogService.recordBestEffort(
                            null, AuditAction.STATE_UPDATE_REJECTED, "APP_STATE", "1", false,
                            ctx.ip(), ctx.header("User-Agent"),
                            Map.of("reason", "invalid_or_expired_token")
                    );
                    ctx.status(401).json(Map.of("error", "Sessão inválida ou expirada."));
                    return;
                }

                if (snapshot.exists()) {
                    String authorizationCurrent;
                    try {
                        RelationallyProtectedState protectedState = protectRelationalSections(
                                dataSource, snapshot.payload(), payload, relationalSliceReader);
                        authorizationCurrent = protectedState.currentPayload();
                        payload = protectedState.candidatePayload();
                    } catch (EventSliceConflictException ex) {
                        auditLogService.recordBestEffort(
                                principal.orElseThrow(), AuditAction.STATE_UPDATE_REJECTED,
                                "EVENT", null, false, ctx.ip(), ctx.header("User-Agent"),
                                Map.of("reason", "relational_events_are_authoritative")
                        );
                        ctx.status(409).json(Map.of(
                                "error", "Eventos devem ser alterados pelos endpoints específicos."
                        ));
                        return;
                    } catch (SessionSliceConflictException ex) {
                        auditLogService.recordBestEffort(
                                principal.orElseThrow(), AuditAction.STATE_UPDATE_REJECTED,
                                "SESSION", null, false, ctx.ip(), ctx.header("User-Agent"),
                                Map.of("reason", "relational_sessions_are_authoritative")
                        );
                        ctx.status(409).json(Map.of(
                                "error", "Partidas devem ser alteradas pelos endpoints específicos."
                        ));
                        return;
                    }
                    StateAuthorizationService.AuthorizationDecision authorization =
                            stateAuthorizationService.authorize(
                                    authorizationCurrent, payload, principal.orElseThrow());
                    if (!authorization.allowed()) {
                        auditLogService.recordBestEffort(
                                principal.orElseThrow(), authorization.auditAction(),
                                authorization.resourceType(), "1", false,
                                ctx.ip(), ctx.header("User-Agent"),
                                Map.of("reason", authorization.reason())
                        );
                        if (authorization.invalidPayload()) {
                            ctx.status(422).json(Map.of("error", "Payload de estado inválido."));
                        } else {
                            ctx.status(403).json(Map.of("error", "Operação não permitida."));
                        }
                        return;
                    }
                }

                List<String> changedSections = changedSections(snapshot.payload(), payload);
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        saveState(connection, payload);
                        auditLogService.record(
                                connection,
                                principal.orElse(null),
                                AuditAction.STATE_UPDATED,
                                "APP_STATE",
                                "1",
                                true,
                                ctx.ip(),
                                ctx.header("User-Agent"),
                                Map.of(
                                        "changedSections", changedSections,
                                        "initialization", !snapshot.exists()
                                )
                        );
                        connection.commit();
                    } catch (Exception ex) {
                        connection.rollback();
                        if (ex instanceof SQLException sqlException) {
                            throw sqlException;
                        }
                        throw new SQLException("State transaction failed", ex);
                    }
                }
                try {
                    br.com.tabula.service.RelationalStateSyncService.syncFromStateJson(
                            dataSource, payload, !snapshot.exists());
                } catch (Exception ex) {
                    LOGGER.atError()
                          .addKeyValue("state_id", 1)
                          .addKeyValue("operation", "state_update")
                          .setCause(ex)
                          .log("Failed to sync relational database state in shadow mode");
                }
                ctx.json(Map.of("ok", true));
            } catch (SQLException ex) {
                auditLogService.recordBestEffort(
                        null, AuditAction.STATE_UPDATE_REJECTED, "APP_STATE", "1", false,
                        ctx.ip(), ctx.header("User-Agent"), Map.of("reason", "persistence_error")
                );
                LOGGER.atError()
                      .addKeyValue("state_id", 1)
                      .addKeyValue("operation", "state_update")
                      .setCause(ex)
                      .log("Failed to save app state");
                ctx.status(500).json(Map.of("error", "Não foi possível salvar os dados."));
            }
        });
    }

    private static String readState(HikariDataSource dataSource) throws SQLException {
        String sql = "SELECT data::text FROM app_state WHERE id = 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) return resultSet.getString(1);
            return null;
        }
    }

    private static StateSnapshot readStateSnapshot(HikariDataSource dataSource) throws SQLException {
        String sql = "SELECT data::text FROM app_state WHERE id = 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) return new StateSnapshot(false, null);
            return new StateSnapshot(true, resultSet.getString(1));
        }
    }

    private static void saveState(Connection connection, String payload) throws SQLException {
        String sql = """
                INSERT INTO app_state (id, data, updated_at)
                VALUES (1, ?::jsonb, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO UPDATE
                SET data = EXCLUDED.data,
                    updated_at = CURRENT_TIMESTAMP
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, payload);
            statement.executeUpdate();
        }
    }

    private static List<String> changedSections(String previousPayload, String nextPayload) {
        List<String> changed = new ArrayList<>();
        try {
            com.fasterxml.jackson.databind.JsonNode previous = previousPayload == null
                    ? MAPPER.createObjectNode()
                    : MAPPER.readTree(previousPayload);
            com.fasterxml.jackson.databind.JsonNode next = MAPPER.readTree(nextPayload);
            for (String section : List.of("users", "boardGames", "sessions", "events")) {
                if (!java.util.Objects.equals(previous.get(section), next.get(section))) {
                    changed.add(section);
                }
            }
        } catch (Exception ignored) {
            // The existing persistence path remains responsible for invalid JSON.
        }
        return changed;
    }

    private static String overlayRelationalSlices(HikariDataSource dataSource, String legacyPayload)
            throws SQLException {
        try {
            com.fasterxml.jackson.databind.JsonNode legacy = MAPPER.readTree(legacyPayload);
            if (!legacy.isObject()) return legacyPayload;
            if (legacy.path("events").isArray()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) legacy)
                        .set("events", relationalEvents(dataSource));
            }
            try {
                ((com.fasterxml.jackson.databind.node.ObjectNode) legacy)
                        .set("sessions", relationalSessions(dataSource));
            } catch (Exception ex) {
                LOGGER.atWarn().addKeyValue("operation", "session_projection")
                        .log("Relational session projection unavailable; preserving legacy slice");
            }
            return MAPPER.writeValueAsString(legacy);
        } catch (Exception ex) {
            throw new SQLException("Failed to overlay relational events", ex);
        }
    }

    private static RelationallyProtectedState protectRelationalSections(
            HikariDataSource dataSource, String previousPayload, String candidatePayload,
            RelationalSliceReader relationalSliceReader)
            throws SQLException, EventSliceConflictException, SessionSliceConflictException {
        try {
            com.fasterxml.jackson.databind.JsonNode previous = MAPPER.readTree(previousPayload);
            com.fasterxml.jackson.databind.JsonNode candidate = MAPPER.readTree(candidatePayload);
            if (!previous.isObject() || !candidate.isObject()) {
                throw new EventSliceConflictException();
            }
            RelationalSlices relationalSlices = relationalSliceReader.read(dataSource);
            com.fasterxml.jackson.databind.JsonNode authoritativeEvents = relationalSlices.events();
            com.fasterxml.jackson.databind.JsonNode suppliedEvents = candidate.get("events");
            if (suppliedEvents != null && !suppliedEvents.equals(authoritativeEvents)) {
                throw new EventSliceConflictException();
            }
            com.fasterxml.jackson.databind.JsonNode authoritativeSessions = relationalSlices.sessions();
            com.fasterxml.jackson.databind.JsonNode suppliedSessions = candidate.get("sessions");
            if (suppliedSessions != null && !suppliedSessions.equals(authoritativeSessions)) {
                throw new SessionSliceConflictException();
            }
            com.fasterxml.jackson.databind.node.ObjectNode authorizationCurrent =
                    (com.fasterxml.jackson.databind.node.ObjectNode) previous;
            authorizationCurrent.set("events", authoritativeEvents.deepCopy());
            authorizationCurrent.set("sessions", authoritativeSessions.deepCopy());
            if (!candidate.has("logs") && previous.has("logs")) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) candidate)
                        .set("logs", previous.get("logs").deepCopy());
            }
            ((com.fasterxml.jackson.databind.node.ObjectNode) candidate)
                    .set("events", authoritativeEvents.deepCopy());
            ((com.fasterxml.jackson.databind.node.ObjectNode) candidate)
                    .set("sessions", authoritativeSessions.deepCopy());
            return new RelationallyProtectedState(
                    MAPPER.writeValueAsString(authorizationCurrent),
                    MAPPER.writeValueAsString(candidate));
        } catch (EventSliceConflictException | SessionSliceConflictException ex) {
            throw ex;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new EventSliceConflictException();
        } catch (Exception ex) {
            throw new SQLException("Failed to protect relational state sections", ex);
        }
    }

    private static com.fasterxml.jackson.databind.node.ArrayNode relationalEvents(HikariDataSource dataSource)
            throws SQLException {
        try {
            com.fasterxml.jackson.databind.JsonNode events = MAPPER.readTree(
                    new EventRepository(dataSource).findAllAsStateJson());
            if (!events.isArray()) throw new SQLException("Relational events projection is not an array");
            return (com.fasterxml.jackson.databind.node.ArrayNode) events;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new SQLException("Failed to parse relational events projection", ex);
        }
    }

    private static com.fasterxml.jackson.databind.node.ArrayNode relationalSessions(HikariDataSource dataSource)
            throws Exception {
        com.fasterxml.jackson.databind.JsonNode state = MAPPER.readTree(
                br.com.tabula.service.RelationalStateReadService.readStateAsJson(dataSource));
        if (!state.path("sessions").isArray()) {
            throw new SQLException("Relational sessions projection is not an array");
        }
        return (com.fasterxml.jackson.databind.node.ArrayNode) state.path("sessions");
    }

    private record StateSnapshot(boolean exists, String payload) {
    }

    private record RelationallyProtectedState(String currentPayload, String candidatePayload) {
    }

    record RelationalSlices(
            com.fasterxml.jackson.databind.node.ArrayNode events,
            com.fasterxml.jackson.databind.node.ArrayNode sessions) {
    }

    @FunctionalInterface
    interface RelationalSliceReader {
        RelationalSlices read(HikariDataSource dataSource) throws Exception;
    }

    private static final class EventSliceConflictException extends RuntimeException {
    }

    private static final class SessionSliceConflictException extends RuntimeException {
    }
}

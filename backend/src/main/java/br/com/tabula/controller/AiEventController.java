package br.com.tabula.controller;

import br.com.tabula.ai.AiConfiguration;
import br.com.tabula.ai.AiProviderException;
import br.com.tabula.ai.LiteLlmClient;
import br.com.tabula.dto.AiEventDraftRequest;
import br.com.tabula.dto.AiEventAssistantResponse;
import br.com.tabula.dto.AiEventRefinementRequest;
import br.com.tabula.model.AuditAction;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.service.AiDraftValidationException;
import br.com.tabula.service.AiEventDraftService;
import br.com.tabula.service.AuditLogService;
import br.com.tabula.service.AiUsageLimiter;
import br.com.tabula.service.AuthenticatedUserService;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public final class AiEventController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiEventController.class);
    private static final Map<String, String> USAGE_LIMIT_RESPONSE = Map.of(
            "code", "AI_USAGE_LIMIT_REACHED",
            "message", "O limite temporário de gerações com IA foi atingido. Tente novamente mais tarde."
    );
    private AiEventController() {}

    public static void register(Javalin app, HikariDataSource dataSource) {
        AiConfiguration configuration;
        try { configuration = AiConfiguration.fromEnvironment(); }
        catch (IllegalArgumentException ex) {
            LOGGER.atWarn().addKeyValue("operation", "ai_configuration").log("AI configuration is invalid");
            configuration = new AiConfiguration(null, java.net.URI.create("https://llm.rodrigor.com"),
                    "gpt-4o-mini", java.time.Duration.ofSeconds(15), java.time.ZoneId.of("America/Sao_Paulo"),
                    300, false, 15, 3, 10);
        }
        LiteLlmClient client = new LiteLlmClient(configuration);
        register(app, new AuthenticatedUserService(dataSource),
                new AiEventDraftService(client, dataSource, configuration.timeZone(), configuration.maxCandidateGames()),
                new AuditLogService(dataSource), new AiUsageLimiter(configuration.maxRequestsPerUserHour(),
                        configuration.maxRequestsPerDay(), configuration.timeZone()),
                configuration.configured(), configuration.model());
    }

    static void register(Javalin app, AuthenticatedUserService auth, AiEventDraftService service,
                         AuditLogService audit, boolean configured, String model) {
        register(app, auth, service, audit,
                new AiUsageLimiter(Integer.MAX_VALUE, Integer.MAX_VALUE, java.time.ZoneId.of("UTC")),
                configured, model);
    }

    static void register(Javalin app, AuthenticatedUserService auth, AiEventDraftService service,
                         AuditLogService audit, AiUsageLimiter limiter, boolean configured, String model) {
        app.post("/ai/event-drafts", ctx -> {
            long started = System.nanoTime();
            LOGGER.atInfo().addKeyValue("operation", "ai_event_draft").addKeyValue("model", model)
                    .log("AI event draft started");
            Optional<AuthenticatedPrincipal> principal = auth.resolve(ctx.header("Authorization"));
            if (principal.isEmpty()) {
                ctx.status(401).json(error("UNAUTHORIZED", "Sessão inválida ou expirada."));
                return;
            }
            AiEventDraftRequest request;
            try { request = ctx.bodyAsClass(AiEventDraftRequest.class); }
            catch (Exception ex) {
                ctx.status(400).json(error("INVALID_REQUEST", "Corpo da requisição inválido."));
                return;
            }
            int promptLength = request.prompt() == null ? 0 : request.prompt().trim().length();
            try {
                AiEventDraftService.validatePrompt(request.prompt());
            } catch (AiDraftValidationException ex) {
                long duration = durationMs(started);
                reject(audit, principal.get(), model, promptLength, "invalid_prompt", "validation",
                        duration, ctx.ip(), ctx.userAgent());
                log(model, false, started, null, "invalid_prompt");
                ctx.status(400).json(error("INVALID_PROMPT", ex.getMessage()));
                return;
            }
            if (!configured) {
                reject(audit, principal.get(), model, promptLength, "not_configured", "configuration",
                        durationMs(started), ctx.ip(), ctx.userAgent());
                ctx.status(503).json(error("AI_NOT_CONFIGURED", "Assistente de IA não configurado."));
                return;
            }
            if (!limiter.tryAcquire(principal.get().getExternalId())) {
                log(model, false, started, null, "usage_limit");
                ctx.status(429).json(USAGE_LIMIT_RESPONSE);
                return;
            }
            try {
                AiEventDraftService.GenerationResult generation = service.generateWithUsage(request.prompt());
                AiEventAssistantResponse response = generation.response();
                long duration = durationMs(started);
                Map<String, Object> auditDetails = usageDetails(model, promptLength, response, duration,
                        generation.usage(), generation.providerCalls());
                AuditAction action = switch (response.status()) {
                    case "draft" -> AuditAction.AI_EVENT_DRAFT_GENERATED;
                    case "needs_clarification" -> AuditAction.AI_EVENT_DRAFT_NEEDS_CLARIFICATION;
                    default -> AuditAction.AI_EVENT_DRAFT_UNSUPPORTED;
                };
                boolean generated = response.draft() != null;
                audit.recordBestEffort(principal.get(), action, "AI_EVENT_DRAFT",
                        null, generated, ctx.ip(), ctx.userAgent(), auditDetails);
                log(model, generated, started, draftGameId(response),
                        generated ? null : response.reasonCode(), generation.usage(), generation.providerCalls());
                ctx.json(response);
            } catch (AiDraftValidationException ex) {
                rejectValidation(audit, principal.get(), model, promptLength, ex,
                        durationMs(started), ctx.ip(), ctx.userAgent());
                logValidationFailure("ai_event_draft", model, started, ex);
                ctx.status(422).json(error("INVALID_AI_RESPONSE", "A resposta da IA não pôde ser validada."));
            } catch (AiProviderException ex) {
                int status = providerStatus(ex.category());
                String category = ex.category().name().toLowerCase(java.util.Locale.ROOT);
                reject(audit, principal.get(), model, promptLength, "provider_failure", category,
                        durationMs(started), ctx.ip(), ctx.userAgent());
                log(model, false, started, null, category, br.com.tabula.ai.AiUsage.empty(), ex.providerCalls());
                ctx.status(status).json(error("AI_PROVIDER_UNAVAILABLE",
                        status == 429 ? "O limite de solicitações da IA foi atingido. Tente novamente em instantes."
                                : "O assistente de IA está temporariamente indisponível."));
            }
        });

        app.post("/ai/event-drafts/refine", ctx -> {
            long started = System.nanoTime();
            Optional<AuthenticatedPrincipal> principal = auth.resolve(ctx.header("Authorization"));
            if (principal.isEmpty()) {
                ctx.status(401).json(error("UNAUTHORIZED", "Sessão inválida ou expirada."));
                return;
            }
            AiEventRefinementRequest request;
            try {
                request = ctx.bodyAsClass(AiEventRefinementRequest.class);
                AiEventDraftService.validateInstruction(request.instruction());
                if (request.currentDraft() == null)
                    throw new AiDraftValidationException("currentDraft é obrigatório.");
            } catch (AiDraftValidationException ex) {
                ctx.status(400).json(error("INVALID_REFINEMENT", ex.getMessage()));
                return;
            } catch (Exception ex) {
                ctx.status(400).json(error("INVALID_REQUEST", "Corpo da requisição inválido."));
                return;
            }
            int instructionLength = request.instruction().trim().length();
            if (!configured) {
                ctx.status(503).json(error("AI_NOT_CONFIGURED", "Assistente de IA não configurado."));
                return;
            }
            if (!limiter.tryAcquire(principal.get().getExternalId())) {
                logRefinement(model, false, started, null, "usage_limit",
                        br.com.tabula.ai.AiUsage.empty(), 0);
                ctx.status(429).json(USAGE_LIMIT_RESPONSE);
                return;
            }
            try {
                AiEventDraftService.GenerationResult refinement =
                        service.refineWithUsage(request.instruction(), request.currentDraft());
                AiEventAssistantResponse response = refinement.response();
                Map<String, Object> details = usageDetails(model, instructionLength, response,
                        durationMs(started), refinement.usage(), refinement.providerCalls());
                boolean refined = response.draft() != null;
                audit.recordBestEffort(principal.get(), refined ? AuditAction.AI_EVENT_DRAFT_REFINED
                                : AuditAction.AI_EVENT_REFINEMENT_REJECTED,
                        "AI_EVENT_DRAFT", null, refined, ctx.ip(), ctx.userAgent(), details);
                logRefinement(model, refined, started, draftGameId(response),
                        refined ? null : response.reasonCode(),
                        refinement.usage(), refinement.providerCalls());
                ctx.json(response);
            } catch (AiDraftValidationException ex) {
                rejectRefinementValidation(audit, principal.get(), model, instructionLength, ex,
                        durationMs(started), ctx.ip(), ctx.userAgent());
                logValidationFailure("ai_event_refinement", model, started, ex);
                ctx.status(422).json(error("INVALID_AI_RESPONSE", "A resposta da IA não pôde ser validada."));
            } catch (AiProviderException ex) {
                int status = providerStatus(ex.category());
                String category = ex.category().name().toLowerCase(java.util.Locale.ROOT);
                rejectRefinement(audit, principal.get(), model, instructionLength, "provider_failure",
                        category, durationMs(started), ctx.ip(), ctx.userAgent());
                logRefinement(model, false, started, null, category,
                        br.com.tabula.ai.AiUsage.empty(), ex.providerCalls());
                ctx.status(status).json(error("AI_PROVIDER_UNAVAILABLE",
                        status == 429 ? "O limite de solicitações da IA foi atingido. Tente novamente em instantes."
                                : "O assistente de IA está temporariamente indisponível."));
            }
        });
    }

    private static void reject(AuditLogService audit, AuthenticatedPrincipal actor, String model, int promptLength,
                               String reason, String category, long duration, String ip, String userAgent) {
        audit.recordBestEffort(actor, AuditAction.AI_EVENT_DRAFT_REJECTED, "AI_EVENT_DRAFT", null,
                false, ip, userAgent, Map.of("model", model, "promptLength", promptLength,
                        "warningCount", 0, "success", false, "failureReason", reason,
                        "failureCategory", category, "durationMs", duration));
    }
    private static void rejectValidation(
            AuditLogService audit, AuthenticatedPrincipal actor, String model, int promptLength,
            AiDraftValidationException exception, long duration, String ip, String userAgent) {
        audit.recordBestEffort(actor, AuditAction.AI_EVENT_DRAFT_REJECTED, "AI_EVENT_DRAFT", null,
                false, ip, userAgent, Map.of("model", model, "promptLength", promptLength,
                        "warningCount", 0, "success", false, "reason", "invalid_ai_response",
                        "reasonCode", exception.reasonCode(), "validationStage", exception.validationStage(),
                        "durationMs", duration));
    }
    private static void log(String model, boolean success, long started, String gameId, String reason) {
        log(model, success, started, gameId, reason, br.com.tabula.ai.AiUsage.empty(), 0);
    }
    private static void log(String model, boolean success, long started, String gameId, String reason,
                            br.com.tabula.ai.AiUsage usage) {
        log(model, success, started, gameId, reason, usage, 1);
    }
    private static void log(String model, boolean success, long started, String gameId, String reason,
                            br.com.tabula.ai.AiUsage usage, int providerCalls) {
        var event = LOGGER.atInfo().addKeyValue("operation", "ai_event_draft").addKeyValue("model", model)
                .addKeyValue("success", success).addKeyValue("duration_ms", (System.nanoTime()-started)/1_000_000)
                .addKeyValue("game_id", gameId).addKeyValue("failure_reason", reason)
                .addKeyValue("provider_calls", providerCalls);
        if (usage.promptTokens() != null) event.addKeyValue("prompt_tokens", usage.promptTokens());
        if (usage.completionTokens() != null) event.addKeyValue("completion_tokens", usage.completionTokens());
        if (usage.totalTokens() != null) event.addKeyValue("total_tokens", usage.totalTokens());
        event.log("AI event draft completed");
    }
    private static void logValidationFailure(
            String operation, String model, long started, AiDraftValidationException exception) {
        LOGGER.atInfo().addKeyValue("operation", operation).addKeyValue("model", model)
                .addKeyValue("success", false).addKeyValue("duration_ms", durationMs(started))
                .addKeyValue("failure_reason", "invalid_ai_response")
                .addKeyValue("reason_code", exception.reasonCode())
                .addKeyValue("validation_stage", exception.validationStage())
                .log("AI event draft validation rejected");
    }
    private static Map<String, Object> usageDetails(String model, int promptLength,
                                                     AiEventAssistantResponse response, long duration,
                                                     br.com.tabula.ai.AiUsage usage, int providerCalls) {
        Map<String, Object> details = new java.util.HashMap<>();
        details.put("model", model);
        details.put("promptLength", promptLength);
        if (response.draft() != null) {
            details.put("resultGameId", response.draft().gameId());
            details.put("warningCount", response.draft().warnings().size());
        } else {
            details.put("warningCount", 0);
            details.put("reasonCode", response.reasonCode());
            details.put("failureReason", response.status());
        }
        details.put("durationMs", duration);
        details.put("success", true);
        details.put("providerCalls", providerCalls);
        if (usage.promptTokens() != null) details.put("promptTokens", usage.promptTokens());
        if (usage.completionTokens() != null) details.put("completionTokens", usage.completionTokens());
        if (usage.totalTokens() != null) details.put("totalTokens", usage.totalTokens());
        return details;
    }
    private static String draftGameId(AiEventAssistantResponse response) {
        return response.draft() == null ? null : response.draft().gameId();
    }
    private static void rejectRefinement(AuditLogService audit, AuthenticatedPrincipal actor, String model,
                                         int instructionLength, String reason, String category, long duration,
                                         String ip, String userAgent) {
        audit.recordBestEffort(actor, AuditAction.AI_EVENT_REFINEMENT_REJECTED, "AI_EVENT_DRAFT", null,
                false, ip, userAgent, Map.of("model", model, "instructionLength", instructionLength,
                        "warningCount", 0, "success", false, "failureReason", reason,
                        "failureCategory", category, "durationMs", duration));
    }
    private static void rejectRefinementValidation(
            AuditLogService audit, AuthenticatedPrincipal actor, String model, int instructionLength,
            AiDraftValidationException exception, long duration, String ip, String userAgent) {
        audit.recordBestEffort(actor, AuditAction.AI_EVENT_REFINEMENT_REJECTED, "AI_EVENT_DRAFT", null,
                false, ip, userAgent, Map.of("model", model, "instructionLength", instructionLength,
                        "warningCount", 0, "success", false, "reason", "invalid_ai_response",
                        "reasonCode", exception.reasonCode(), "validationStage", exception.validationStage(),
                        "durationMs", duration));
    }
    private static void logRefinement(String model, boolean success, long started, String gameId, String reason,
                                      br.com.tabula.ai.AiUsage usage, int providerCalls) {
        var event = LOGGER.atInfo().addKeyValue("operation", "ai_event_refinement").addKeyValue("model", model)
                .addKeyValue("success", success).addKeyValue("duration_ms", durationMs(started))
                .addKeyValue("game_id", gameId).addKeyValue("failure_reason", reason)
                .addKeyValue("provider_calls", providerCalls);
        if (usage.promptTokens() != null) event.addKeyValue("prompt_tokens", usage.promptTokens());
        if (usage.completionTokens() != null) event.addKeyValue("completion_tokens", usage.completionTokens());
        if (usage.totalTokens() != null) event.addKeyValue("total_tokens", usage.totalTokens());
        event.log("AI event refinement completed");
    }
    private static long durationMs(long started) { return (System.nanoTime() - started) / 1_000_000; }
    private static Map<String, String> error(String code, String message) {
        return Map.of("code", code, "error", message);
    }
    private static int providerStatus(AiProviderException.Category category) {
        if (category == AiProviderException.Category.RATE_LIMITED) return 429;
        if (category == AiProviderException.Category.NOT_CONFIGURED
                || category == AiProviderException.Category.TIMEOUT
                || category == AiProviderException.Category.CONNECTION
                || category == AiProviderException.Category.SERVER_ERROR
                || category == AiProviderException.Category.CATALOG_UNAVAILABLE) return 503;
        return 502;
    }
}

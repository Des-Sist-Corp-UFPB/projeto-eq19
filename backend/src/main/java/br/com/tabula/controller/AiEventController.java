package br.com.tabula.controller;

import br.com.tabula.ai.AiConfiguration;
import br.com.tabula.ai.AiProviderException;
import br.com.tabula.ai.LiteLlmClient;
import br.com.tabula.dto.AiEventDraftRequest;
import br.com.tabula.dto.AiEventDraftResponse;
import br.com.tabula.model.AuditAction;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.service.AiDraftValidationException;
import br.com.tabula.service.AiEventDraftService;
import br.com.tabula.service.AuditLogService;
import br.com.tabula.service.AuthenticatedUserService;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public final class AiEventController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiEventController.class);
    private AiEventController() {}

    public static void register(Javalin app, HikariDataSource dataSource) {
        AiConfiguration configuration;
        try { configuration = AiConfiguration.fromEnvironment(); }
        catch (IllegalArgumentException ex) {
            LOGGER.atWarn().addKeyValue("operation", "ai_configuration").log("AI configuration is invalid");
            configuration = new AiConfiguration(null, java.net.URI.create("https://llm.rodrigor.com"),
                    "gpt-4o-mini", java.time.Duration.ofSeconds(15), java.time.ZoneId.of("America/Sao_Paulo"));
        }
        register(app, new AuthenticatedUserService(dataSource),
                new AiEventDraftService(new LiteLlmClient(configuration), dataSource, configuration.timeZone()),
                new AuditLogService(dataSource), configuration.configured(), configuration.model());
    }

    static void register(Javalin app, AuthenticatedUserService auth, AiEventDraftService service,
                         AuditLogService audit, boolean configured, String model) {
        app.post("/ai/event-drafts", ctx -> {
            long started = System.nanoTime();
            Optional<AuthenticatedPrincipal> principal = auth.resolve(ctx.header("Authorization"));
            if (principal.isEmpty()) { ctx.status(401).json(Map.of("error", "Sessão inválida ou expirada.")); return; }
            AiEventDraftRequest request;
            try { request = ctx.bodyAsClass(AiEventDraftRequest.class); }
            catch (Exception ex) { ctx.status(400).json(Map.of("error", "Corpo da requisição inválido.")); return; }
            int promptLength = request.prompt() == null ? 0 : request.prompt().trim().length();
            if (!configured) {
                reject(audit, principal.get(), model, promptLength, "not_configured", ctx.ip(), ctx.userAgent());
                ctx.status(503).json(Map.of("error", "Assistente de IA não configurado.")); return;
            }
            try {
                AiEventDraftResponse response = service.generate(request.prompt());
                audit.recordBestEffort(principal.get(), AuditAction.AI_EVENT_DRAFT_GENERATED, "AI_EVENT_DRAFT",
                        null, true, ctx.ip(), ctx.userAgent(), Map.of("model", model, "promptLength", promptLength,
                                "resultGameId", response.gameId(), "warningCount", response.warnings().size(), "success", true));
                log(model, true, started, response.gameId(), null);
                ctx.json(response);
            } catch (AiDraftValidationException ex) {
                String reason = promptLength < 5 || promptLength > 1000 ? "invalid_prompt" : "invalid_ai_response";
                reject(audit, principal.get(), model, promptLength, reason, ctx.ip(), ctx.userAgent());
                log(model, false, started, null, reason);
                ctx.status("invalid_prompt".equals(reason) ? 400 : 422).json(Map.of("error", ex.getMessage()));
            } catch (AiProviderException ex) {
                reject(audit, principal.get(), model, promptLength, "provider_failure", ctx.ip(), ctx.userAgent());
                log(model, false, started, null, "provider_failure");
                ctx.status(502).json(Map.of("error", "O provedor de IA está temporariamente indisponível."));
            }
        });
    }

    private static void reject(AuditLogService audit, AuthenticatedPrincipal actor, String model, int promptLength,
                               String reason, String ip, String userAgent) {
        audit.recordBestEffort(actor, AuditAction.AI_EVENT_DRAFT_REJECTED, "AI_EVENT_DRAFT", null,
                false, ip, userAgent, Map.of("model", model, "promptLength", promptLength,
                        "warningCount", 0, "success", false, "failureReason", reason));
    }
    private static void log(String model, boolean success, long started, String gameId, String reason) {
        LOGGER.atInfo().addKeyValue("operation", "ai_event_draft").addKeyValue("model", model)
                .addKeyValue("success", success).addKeyValue("duration_ms", (System.nanoTime()-started)/1_000_000)
                .addKeyValue("game_id", gameId).addKeyValue("failure_reason", reason).log("AI event draft completed");
    }
}

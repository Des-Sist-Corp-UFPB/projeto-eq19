package br.com.tabula.controller;

import br.com.tabula.ai.AiProviderException;
import br.com.tabula.ai.AiUsage;
import br.com.tabula.dto.AiEventDraftResponse;
import br.com.tabula.dto.AiEventAssistantResponse;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.model.AuditAction;
import br.com.tabula.service.AiDraftValidationException;
import br.com.tabula.service.AiEventDraftService;
import br.com.tabula.service.AuditLogService;
import br.com.tabula.service.AiUsageLimiter;
import br.com.tabula.service.AuthenticatedUserService;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiEventControllerTest {
    private Javalin app;

    @AfterEach void stop() { if (app != null) app.stop(); }

    @Test void returns401WithoutOrWithInvalidToken() throws Exception {
        AuthenticatedUserService auth = mock(AuthenticatedUserService.class);
        when(auth.resolve(any())).thenReturn(Optional.empty());
        start(auth, mock(AiEventDraftService.class), true);
        assertEquals(401, post(null, "{\"prompt\":\"Mesa válida\"}").statusCode());
        assertEquals(401, post("Bearer invalid", "{\"prompt\":\"Mesa válida\"}").statusCode());
    }

    @Test void returns400BeforeCallingProviderForInvalidPrompt() throws Exception {
        AiEventDraftService service = mock(AiEventDraftService.class);
        start(authenticated(), service, true);
        HttpResponse<String> response = post("Bearer valid", "{\"prompt\":\"  \"}");
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("INVALID_PROMPT"));
        verify(service, never()).generateWithUsage(any());
    }

    @Test void returns503WhenNotConfigured() throws Exception {
        AiEventDraftService service = mock(AiEventDraftService.class);
        start(authenticated(), service, false);
        assertEquals(503, post("Bearer valid", "{\"prompt\":\"Mesa válida\"}").statusCode());
        verify(service, never()).generateWithUsage(any());
    }

    @Test void returns200ForValidDraft() throws Exception {
        AiEventDraftService service = mock(AiEventDraftService.class);
        when(service.generateWithUsage(any())).thenReturn(new AiEventDraftService.GenerationResult(
                draftResponse(), AiUsage.empty()));
        start(authenticated(), service, true);
        HttpResponse<String> response = post("Bearer valid", "{\"prompt\":\"Mesa válida\"}");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"draft\""));
        assertTrue(response.body().contains("\"gameId\":\"g2\""));
    }

    @Test void returnsStructuredClarificationAndUnsupportedWithoutDraft() throws Exception {
        AiEventDraftService service = mock(AiEventDraftService.class);
        when(service.generateWithUsage(any()))
                .thenReturn(new AiEventDraftService.GenerationResult(
                        AiEventAssistantResponse.needsClarification("missing_required_information",
                                List.of("date", "location"), "Informe data e local."), AiUsage.empty()))
                .thenReturn(new AiEventDraftService.GenerationResult(
                        AiEventAssistantResponse.unsupported("not_event_creation_request"), AiUsage.empty()));
        start(authenticated(), service, true);

        HttpResponse<String> clarification = post("Bearer valid", "{\"prompt\":\"Quero criar evento de Magic\"}");
        HttpResponse<String> unsupported = post("Bearer valid", "{\"prompt\":\"Qual o horário do SBT hoje?\"}");

        assertEquals(200, clarification.statusCode());
        assertTrue(clarification.body().contains("\"status\":\"needs_clarification\""));
        assertFalse(clarification.body().contains("\"draft\""));
        assertEquals(200, unsupported.statusCode());
        assertTrue(unsupported.body().contains("\"status\":\"unsupported\""));
        assertFalse(unsupported.body().contains("\"draft\""));
    }

    @Test void mapsInvalidModelOutputAndProviderFailures() throws Exception {
        AiEventDraftService service = mock(AiEventDraftService.class);
        when(service.generateWithUsage(any()))
                .thenThrow(new AiDraftValidationException("raw detail"))
                .thenThrow(new AiProviderException(AiProviderException.Category.RATE_LIMITED))
                .thenThrow(new AiProviderException(AiProviderException.Category.TIMEOUT));
        start(authenticated(), service, true);
        HttpResponse<String> invalid = post("Bearer valid", "{\"prompt\":\"Mesa válida\"}");
        assertEquals(422, invalid.statusCode());
        assertFalse(invalid.body().contains("raw detail"));
        assertEquals(429, post("Bearer valid", "{\"prompt\":\"Mesa válida\"}").statusCode());
        assertEquals(503, post("Bearer valid", "{\"prompt\":\"Mesa válida\"}").statusCode());
    }

    @Test void auditsOneSafeSpecificReasonAndKeepsTheExisting422Contract() throws Exception {
        AiEventDraftService service = mock(AiEventDraftService.class);
        AuditLogService audit = mock(AuditLogService.class);
        when(service.generateWithUsage(any())).thenThrow(new AiDraftValidationException(
                "raw model detail must not escape", "game_not_in_catalog", "draft_validation"));
        start(authenticated(), service, audit, true,
                new AiUsageLimiter(Integer.MAX_VALUE, Integer.MAX_VALUE, java.time.ZoneId.of("UTC")));

        HttpResponse<String> response = post("Bearer secret-token", "{\"prompt\":\"private prompt text\"}");

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("INVALID_AI_RESPONSE"));
        assertFalse(response.body().contains("raw model detail"));
        verify(audit, times(1)).recordBestEffort(any(), eq(AuditAction.AI_EVENT_DRAFT_REJECTED),
                eq("AI_EVENT_DRAFT"), isNull(), eq(false), any(), any(), argThat(details ->
                        "invalid_ai_response".equals(details.get("reason"))
                                && "game_not_in_catalog".equals(details.get("reasonCode"))
                                && "draft_validation".equals(details.get("validationStage"))
                                && "gpt-4o-mini".equals(details.get("model"))
                                && !details.containsKey("prompt")
                                && !details.containsKey("authorization")
                                && !details.toString().contains("private prompt text")
                                && !details.toString().contains("secret-token")
                                && !details.toString().contains("raw model detail")));
    }

    @Test void rejectsUsageLimitBeforeExternalCall() throws Exception {
        AiEventDraftService service = mock(AiEventDraftService.class);
        start(authenticated(), service, true,
                new AiUsageLimiter(1, 1, java.time.ZoneId.of("America/Sao_Paulo")));
        when(service.generateWithUsage(any())).thenReturn(new AiEventDraftService.GenerationResult(
                draftResponse(), AiUsage.empty()));
        assertEquals(200, post("Bearer valid", "{\"prompt\":\"Mesa válida\"}").statusCode());
        HttpResponse<String> limited = post("Bearer valid", "{\"prompt\":\"Mesa válida\"}");
        assertEquals(429, limited.statusCode());
        assertTrue(limited.body().contains("AI_USAGE_LIMIT_REACHED"));
        verify(service, times(1)).generateWithUsage(any());
    }

    @Test void generationAndRefinementShareLimitAndRejectedRefinementDoesNotCallProvider() throws Exception {
        AiEventDraftService service = mock(AiEventDraftService.class);
        when(service.generateWithUsage(any())).thenReturn(new AiEventDraftService.GenerationResult(
                draftResponse(), AiUsage.empty()));
        start(authenticated(), service, true,
                new AiUsageLimiter(1, 10, java.time.ZoneId.of("America/Sao_Paulo")));

        assertEquals(200, post("Bearer valid", "{\"prompt\":\"Mesa válida\"}").statusCode());
        HttpResponse<String> limited = post("/ai/event-drafts/refine", "Bearer valid", """
                {"instruction":"Troque o horário","currentDraft":{"gameId":"g2","gameName":"Magic",
                "date":"2026-08-01","time":"18:00","location":"Biblioteca","maxParticipants":4,
                "description":"Mesa.","warnings":[]}}
                """);
        assertEquals(429, limited.statusCode());
        assertTrue(limited.body().contains("AI_USAGE_LIMIT_REACHED"));
        verify(service, never()).refineWithUsage(any(), any());
    }

    private void start(AuthenticatedUserService auth, AiEventDraftService service, boolean configured) {
        start(auth, service, configured,
                new AiUsageLimiter(Integer.MAX_VALUE, Integer.MAX_VALUE, java.time.ZoneId.of("UTC")));
    }

    private void start(AuthenticatedUserService auth, AiEventDraftService service, boolean configured,
                       AiUsageLimiter limiter) {
        start(auth, service, mock(AuditLogService.class), configured, limiter);
    }

    private void start(AuthenticatedUserService auth, AiEventDraftService service, AuditLogService audit,
                       boolean configured, AiUsageLimiter limiter) {
        app = Javalin.create(config -> config.showJavalinBanner = false);
        AiEventController.register(app, auth, service, audit, limiter,
                configured, "gpt-4o-mini");
        app.start(0);
    }

    private static AuthenticatedUserService authenticated() throws Exception {
        AuthenticatedUserService auth = mock(AuthenticatedUserService.class);
        when(auth.resolve(any())).thenReturn(Optional.of(new AuthenticatedPrincipal(1L, "u1", "student")));
        return auth;
    }

    private HttpResponse<String> post(String authorization, String body) throws Exception {
        return post("/ai/event-drafts", authorization, body);
    }

    private static AiEventAssistantResponse draftResponse() {
        return AiEventAssistantResponse.draft(new AiEventDraftResponse("g2", "Magic", "2026-08-01", "18:00",
                "Biblioteca", 4, "Mesa.", List.of()));
    }

    private HttpResponse<String> post(String path, String authorization, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (authorization != null) request.header("Authorization", authorization);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}

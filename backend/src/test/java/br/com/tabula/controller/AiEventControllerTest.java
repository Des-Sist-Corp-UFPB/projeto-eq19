package br.com.tabula.controller;

import br.com.tabula.ai.AiProviderException;
import br.com.tabula.dto.AiEventDraftResponse;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.service.AiDraftValidationException;
import br.com.tabula.service.AiEventDraftService;
import br.com.tabula.service.AuditLogService;
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
        verify(service, never()).generate(any());
    }

    @Test void returns503WhenNotConfigured() throws Exception {
        AiEventDraftService service = mock(AiEventDraftService.class);
        start(authenticated(), service, false);
        assertEquals(503, post("Bearer valid", "{\"prompt\":\"Mesa válida\"}").statusCode());
        verify(service, never()).generate(any());
    }

    @Test void returns200ForValidDraft() throws Exception {
        AiEventDraftService service = mock(AiEventDraftService.class);
        when(service.generate(any())).thenReturn(new AiEventDraftResponse(
                "g2", "Magic", "2026-08-01", "18:00", "Biblioteca", 4, "Mesa.", List.of()));
        start(authenticated(), service, true);
        HttpResponse<String> response = post("Bearer valid", "{\"prompt\":\"Mesa válida\"}");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"gameId\":\"g2\""));
    }

    @Test void mapsInvalidModelOutputAndProviderFailures() throws Exception {
        AiEventDraftService service = mock(AiEventDraftService.class);
        when(service.generate(any()))
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

    private void start(AuthenticatedUserService auth, AiEventDraftService service, boolean configured) {
        app = Javalin.create(config -> config.showJavalinBanner = false);
        AiEventController.register(app, auth, service, mock(AuditLogService.class),
                configured, "gpt-4o-mini");
        app.start(0);
    }

    private static AuthenticatedUserService authenticated() throws Exception {
        AuthenticatedUserService auth = mock(AuthenticatedUserService.class);
        when(auth.resolve(any())).thenReturn(Optional.of(new AuthenticatedPrincipal(1L, "u1", "student")));
        return auth;
    }

    private HttpResponse<String> post(String authorization, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + "/ai/event-drafts"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (authorization != null) request.header("Authorization", authorization);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}

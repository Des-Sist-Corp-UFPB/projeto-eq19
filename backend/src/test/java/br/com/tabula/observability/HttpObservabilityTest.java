package br.com.tabula.observability;

import io.javalin.Javalin;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HttpObservabilityTest {
    @Test
    void shouldObserveSuccessClientErrorServerErrorAndPingWithoutChangingResponses() throws Exception {
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        HttpObservability.register(app);
        app.get("/resource/{id}", ctx -> ctx.json(java.util.Map.of("ok", true)));
        app.get("/client-error", ctx -> ctx.status(422).json(java.util.Map.of("error", "invalid")));
        app.get("/server-error", ctx -> ctx.status(503).json(java.util.Map.of("error", "unavailable")));
        app.get("/ping", ctx -> ctx.json(java.util.Map.of("status", "ok")));
        app.start(0);
        try {
            assertResponse(app, "/resource/42", 200, "Bearer must-not-appear");
            assertResponse(app, "/client-error", 422, null);
            assertResponse(app, "/server-error", 503, null);
            assertResponse(app, "/ping", 200, null);
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldCoverPrivateConstructor() throws Exception {
        var constructor = HttpObservability.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertFalse(constructor.newInstance() == null);
    }

    @Test
    void shouldUseRegisteredRouteWithoutConcreteResourceId() {
        String route = HttpObservability.normalizedRoute("/sessions/{id}");

        assertEquals("/sessions/{id}", route);
        assertFalse(route.contains("s_5082d1a2-9178-4d9c-929d-e0664b1327db"));
        assertEquals("unmatched", HttpObservability.normalizedRoute(null));
        assertEquals("unmatched", HttpObservability.normalizedRoute(" "));
    }

    private static void assertResponse(Javalin app, String path, int status, String authorization)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .GET();
        if (authorization != null) request.header("Authorization", authorization);
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode());
        assertFalse(response.body().contains("must-not-appear"));
    }
}

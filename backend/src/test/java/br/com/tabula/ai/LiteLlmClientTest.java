package br.com.tabula.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LiteLlmClientTest {
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void sendsBearerAndExtractsContent() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}");
        });
        server.start();
        assertEquals("{\"ok\":true}", client().chat("system", "user"));
    }

    @Test void doesNotRetryUnauthorized() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        startStatusServer(calls, 401, "{}");
        AiProviderException error = assertThrows(AiProviderException.class, () -> client().chat("s", "u"));
        assertEquals(AiProviderException.Category.UNAUTHORIZED, error.category());
        assertEquals(1, calls.get());
    }

    @Test void retriesRateLimitOnlyOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            int call = calls.incrementAndGet();
            respond(exchange, call == 1 ? 429 : 200,
                    call == 1 ? "{}" : "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}");
        });
        server.start();
        assertEquals("{}", client().chat("s", "u"));
        assertEquals(2, calls.get());
    }

    @Test void classifiesServerErrorAfterSingleRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        startStatusServer(calls, 500, "{}");
        AiProviderException error = assertThrows(AiProviderException.class, () -> client().chat("s", "u"));
        assertEquals(AiProviderException.Category.SERVER_ERROR, error.category());
        assertEquals(2, calls.get());
    }

    @Test void rejectsEmptyMalformedAndMissingChoicesResponses() throws Exception {
        for (String body : new String[]{"", "not-json", "{}", "{\"choices\":[]}",
                "{\"choices\":[{\"message\":{}}]}"}) {
            if (server != null) server.stop(0);
            AtomicInteger calls = new AtomicInteger();
            startStatusServer(calls, 200, body);
            AiProviderException error = assertThrows(AiProviderException.class, () -> client().chat("s", "u"));
            assertEquals(AiProviderException.Category.INVALID_RESPONSE, error.category(), "body=" + body);
            assertEquals(1, calls.get());
        }
    }

    @Test void refusesCallWithoutConfiguration() {
        AiConfiguration config = new AiConfiguration(null, URI.create("https://example.invalid"),
                "gpt-4o-mini", Duration.ofSeconds(1), ZoneId.of("America/Sao_Paulo"));
        AiProviderException error = assertThrows(AiProviderException.class,
                () -> new LiteLlmClient(config).chat("s", "u"));
        assertEquals(AiProviderException.Category.NOT_CONFIGURED, error.category());
    }

    private void startStatusServer(AtomicInteger calls, int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            calls.incrementAndGet();
            respond(exchange, status, body);
        });
        server.start();
    }

    private LiteLlmClient client() {
        AiConfiguration config = new AiConfiguration("test-key",
                URI.create("http://localhost:" + server.getAddress().getPort()), "gpt-4o-mini",
                Duration.ofSeconds(2), ZoneId.of("America/Sao_Paulo"));
        return new LiteLlmClient(config, HttpClient.newHttpClient());
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

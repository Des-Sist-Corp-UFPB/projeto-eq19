package br.com.tabula.controller;

import io.javalin.Javalin;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PingControllerTest {

    @Test
    void shouldExposeHealthEndpoint() throws Exception {
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        PingController.register(app);
        app.start(0);

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + app.port() + "/ping"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"status\":\"ok\""));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldCoverPrivateConstructor() throws Exception {
        java.lang.reflect.Constructor<PingController> constructor = PingController.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        PingController instance = constructor.newInstance();
        assertTrue(instance != null);
    }
}

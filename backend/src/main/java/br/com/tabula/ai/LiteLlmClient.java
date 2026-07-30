package br.com.tabula.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class LiteLlmClient implements AiChatClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AiConfiguration configuration;
    private final HttpClient httpClient;

    public LiteLlmClient(AiConfiguration configuration) {
        this(configuration, HttpClient.newBuilder().connectTimeout(configuration.timeout()).build());
    }

    LiteLlmClient(AiConfiguration configuration, HttpClient httpClient) {
        this.configuration = configuration;
        this.httpClient = httpClient;
    }

    @Override public String model() { return configuration.model(); }

    @Override
    public String chat(String systemPrompt, String userPrompt) throws AiProviderException {
        if (!configuration.configured()) throw new AiProviderException("AI_NOT_CONFIGURED");
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", configuration.model());
            body.put("temperature", 0.2);
            body.put("max_tokens", 500);
            body.putObject("response_format").put("type", "json_object");
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);
            HttpRequest request = HttpRequest.newBuilder(configuration.baseUrl().resolve("/chat/completions"))
                    .timeout(configuration.timeout())
                    .header("Authorization", "Bearer " + configuration.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new AiProviderException("AI_PROVIDER_FAILURE");
            JsonNode content = MAPPER.readTree(response.body()).path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) throw new AiProviderException("AI_PROVIDER_INVALID_RESPONSE");
            return content.asText();
        } catch (AiProviderException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("AI_PROVIDER_INTERRUPTED", ex);
        } catch (Exception ex) {
            throw new AiProviderException("AI_PROVIDER_FAILURE", ex);
        }
    }
}

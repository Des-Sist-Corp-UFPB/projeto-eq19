package br.com.tabula.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;

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
        if (!configuration.configured()) throw new AiProviderException(AiProviderException.Category.NOT_CONFIGURED);
        AiProviderException firstFailure;
        try {
            return send(systemPrompt, userPrompt);
        } catch (AiProviderException ex) {
            firstFailure = ex;
        }
        if (!firstFailure.transientFailure()) throw firstFailure;
        return send(systemPrompt, userPrompt);
    }

    private String send(String systemPrompt, String userPrompt) throws AiProviderException {
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", configuration.model());
            body.put("temperature", 0.2);
            body.put("max_tokens", 500);
            body.putObject("response_format").put("type", "json_object");
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);
            String endpoint = configuration.baseUrl().toString() + "/chat/completions";
            HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(endpoint))
                    .timeout(configuration.timeout()).header("Authorization", "Bearer " + configuration.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401 || status == 403) throw new AiProviderException(AiProviderException.Category.UNAUTHORIZED);
            if (status == 429) throw new AiProviderException(AiProviderException.Category.RATE_LIMITED);
            if (status >= 500) throw new AiProviderException(AiProviderException.Category.SERVER_ERROR);
            if (status < 200 || status >= 300) throw new AiProviderException(AiProviderException.Category.INVALID_RESPONSE);
            if (response.body() == null || response.body().isBlank())
                throw new AiProviderException(AiProviderException.Category.INVALID_RESPONSE);
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty())
                throw new AiProviderException(AiProviderException.Category.INVALID_RESPONSE);
            JsonNode content = choices.get(0).path("message").get("content");
            if (content == null || !content.isTextual() || content.asText().isBlank())
                throw new AiProviderException(AiProviderException.Category.INVALID_RESPONSE);
            return content.asText();
        } catch (AiProviderException ex) { throw ex; }
        catch (HttpTimeoutException ex) { throw new AiProviderException(AiProviderException.Category.TIMEOUT, ex); }
        catch (ConnectException ex) { throw new AiProviderException(AiProviderException.Category.CONNECTION, ex); }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(AiProviderException.Category.INTERRUPTED, ex);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new AiProviderException(AiProviderException.Category.INVALID_RESPONSE, ex);
        } catch (java.io.IOException ex) {
            throw new AiProviderException(AiProviderException.Category.CONNECTION, ex);
        } catch (Exception ex) {
            throw new AiProviderException(AiProviderException.Category.INVALID_RESPONSE, ex);
        }
    }
}

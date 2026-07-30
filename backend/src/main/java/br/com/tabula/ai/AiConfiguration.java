package br.com.tabula.ai;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;

public record AiConfiguration(String apiKey, URI baseUrl, String model, Duration timeout, ZoneId timeZone) {
    public static AiConfiguration fromEnvironment() { return from(System.getenv()); }

    static AiConfiguration from(Map<String, String> env) {
        String key = clean(env.get("LITELLM_API_KEY"));
        String url = value(env, "LITELLM_BASE_URL", "https://llm.rodrigor.com");
        String model = value(env, "LITELLM_MODEL", "gpt-4o-mini");
        int seconds;
        try { seconds = Integer.parseInt(value(env, "AI_REQUEST_TIMEOUT_SECONDS", "15")); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("AI_REQUEST_TIMEOUT_SECONDS inválido."); }
        if (seconds < 1 || seconds > 120) throw new IllegalArgumentException("AI_REQUEST_TIMEOUT_SECONDS inválido.");
        URI uri;
        ZoneId zone;
        try { uri = URI.create(url.replaceAll("/+$", "")); }
        catch (Exception ex) { throw new IllegalArgumentException("LITELLM_BASE_URL inválida."); }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
            throw new IllegalArgumentException("LITELLM_BASE_URL inválida.");
        if (uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("LITELLM_BASE_URL inválida.");
        try { zone = ZoneId.of(value(env, "APP_TIME_ZONE", "America/Sao_Paulo")); }
        catch (Exception ex) { throw new IllegalArgumentException("APP_TIME_ZONE inválida."); }
        return new AiConfiguration(key, uri, model, Duration.ofSeconds(seconds), zone);
    }

    public boolean configured() { return apiKey != null; }
    private static String value(Map<String,String> env, String key, String fallback) {
        String value = clean(env.get(key)); return value == null ? fallback : value;
    }
    private static String clean(String value) {
        if (value == null || value.isBlank() || value.startsWith("<")) return null;
        return value.trim();
    }
}

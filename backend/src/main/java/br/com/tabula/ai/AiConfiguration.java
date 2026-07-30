package br.com.tabula.ai;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;

public record AiConfiguration(
        String apiKey, URI baseUrl, String model, Duration timeout, ZoneId timeZone,
        int maxCompletionTokens, boolean retryEnabled, int maxCandidateGames,
        int maxRequestsPerUserHour, int maxRequestsPerDay) {
    public AiConfiguration(String apiKey, URI baseUrl, String model, Duration timeout, ZoneId timeZone) {
        this(apiKey, baseUrl, model, timeout, timeZone, 300, false, 15, 3, 10);
    }

    public static AiConfiguration fromEnvironment() { return from(System.getenv()); }

    static AiConfiguration from(Map<String, String> env) {
        String key = clean(env.get("LITELLM_API_KEY"));
        String url = value(env, "LITELLM_BASE_URL", "https://llm.rodrigor.com");
        String model = value(env, "LITELLM_MODEL", "gpt-4o-mini");
        int seconds = integer(env, "AI_REQUEST_TIMEOUT_SECONDS", 15, 1, 120);
        int maxTokens = integer(env, "AI_MAX_COMPLETION_TOKENS", 300, 50, 1000);
        int maxCandidates = integer(env, "AI_MAX_CANDIDATE_GAMES", 15, 1, 50);
        int perUser = integer(env, "AI_MAX_REQUESTS_PER_USER_HOUR", 3, 1, 1000);
        int perDay = integer(env, "AI_MAX_REQUESTS_PER_DAY", 10, 1, 10000);
        boolean retryEnabled = Boolean.parseBoolean(value(env, "AI_RETRY_ENABLED", "false"));
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
        return new AiConfiguration(key, uri, model, Duration.ofSeconds(seconds), zone, maxTokens,
                retryEnabled, maxCandidates, perUser, perDay);
    }

    public boolean configured() { return apiKey != null; }
    private static String value(Map<String,String> env, String key, String fallback) {
        String value = clean(env.get(key)); return value == null ? fallback : value;
    }
    private static String clean(String value) {
        if (value == null || value.isBlank() || value.startsWith("<")) return null;
        return value.trim();
    }
    private static int integer(Map<String, String> env, String key, int fallback, int min, int max) {
        try {
            int parsed = Integer.parseInt(value(env, key, String.valueOf(fallback)));
            if (parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(key + " inválido.");
        }
    }
}

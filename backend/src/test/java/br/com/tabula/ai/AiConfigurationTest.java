package br.com.tabula.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiConfigurationTest {
    @Test void usesSafeDefaultsAndTreatsPlaceholderAsUnconfigured() {
        AiConfiguration config = AiConfiguration.from(Map.of("LITELLM_API_KEY", "<CHAVE>"));
        assertFalse(config.configured());
        assertEquals("gpt-4o-mini", config.model());
        assertEquals(15, config.timeout().toSeconds());
        assertEquals(300, config.maxCompletionTokens());
        assertFalse(config.retryEnabled());
        assertEquals(15, config.maxCandidateGames());
        assertEquals(3, config.maxRequestsPerUserHour());
        assertEquals(10, config.maxRequestsPerDay());
    }

    @Test void validatesUrlTimeoutAndTimezone() {
        assertThrows(IllegalArgumentException.class,
                () -> AiConfiguration.from(Map.of("LITELLM_BASE_URL", "file:///tmp/key")));
        assertThrows(IllegalArgumentException.class,
                () -> AiConfiguration.from(Map.of("AI_REQUEST_TIMEOUT_SECONDS", "0")));
        assertThrows(IllegalArgumentException.class,
                () -> AiConfiguration.from(Map.of("APP_TIME_ZONE", "invalid-zone")));
    }
}

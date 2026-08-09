package br.com.tabula.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NginxHealthcheckConfigTest {
    private static final Pattern EXACT_PING_LOCATION = Pattern.compile(
            "location\\s*=\\s*/ping\\s*\\{(?<body>.*?)\\}",
            Pattern.DOTALL
    );

    @Test
    void shouldProxyExactPingRouteBeforeSpaFallback() throws Exception {
        String config = Files.readString(findNginxConfig());
        Matcher pingLocation = EXACT_PING_LOCATION.matcher(config);

        assertTrue(pingLocation.find(), "Nginx must define an exact /ping location");
        assertTrue(pingLocation.group("body").contains("proxy_pass http://127.0.0.1:8119/ping;"));
        assertFalse(pingLocation.group("body").contains("index.html"));
        assertTrue(pingLocation.start() < config.indexOf("try_files $uri $uri/ /index.html;"));
    }

    private static Path findNginxConfig() {
        Path fromRepositoryRoot = Path.of("docker", "nginx.conf");
        if (Files.exists(fromRepositoryRoot)) return fromRepositoryRoot;
        return Path.of("..", "docker", "nginx.conf");
    }
}

package br.com.tabula.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class AiUsageLimiterTest {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZONE);

    @Test void enforcesPerUserAndGlobalLimits() {
        AiUsageLimiter perUser = new AiUsageLimiter(2, 10, ZONE, CLOCK);
        assertTrue(perUser.tryAcquire("u1"));
        assertTrue(perUser.tryAcquire("u1"));
        assertFalse(perUser.tryAcquire("u1"));
        assertTrue(perUser.tryAcquire("u2"));

        AiUsageLimiter global = new AiUsageLimiter(10, 2, ZONE, CLOCK);
        assertTrue(global.tryAcquire("u1"));
        assertTrue(global.tryAcquire("u2"));
        assertFalse(global.tryAcquire("u3"));
    }

    @Test void isThreadSafeAndNeverExceedsGlobalLimit() throws Exception {
        AiUsageLimiter limiter = new AiUsageLimiter(100, 20, ZONE, CLOCK);
        var executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            final int user = i;
            futures.add(executor.submit(() -> { start.await(); return limiter.tryAcquire("u" + user); }));
        }
        start.countDown();
        int accepted = 0;
        for (var future : futures) if (future.get()) accepted++;
        executor.shutdownNow();
        assertEquals(20, accepted);
    }
}

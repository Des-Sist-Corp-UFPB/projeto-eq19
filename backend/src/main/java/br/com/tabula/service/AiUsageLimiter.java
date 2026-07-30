package br.com.tabula.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

public final class AiUsageLimiter {
    private final int perUserHour;
    private final int globalDay;
    private final ZoneId zone;
    private final Clock clock;
    private final ConcurrentHashMap<String, Integer> userCounts = new ConcurrentHashMap<>();
    private LocalDate currentDay;
    private int dayCount;

    public AiUsageLimiter(int perUserHour, int globalDay, ZoneId zone) {
        this(perUserHour, globalDay, zone, Clock.system(zone));
    }

    AiUsageLimiter(int perUserHour, int globalDay, ZoneId zone, Clock clock) {
        this.perUserHour = perUserHour;
        this.globalDay = globalDay;
        this.zone = zone;
        this.clock = clock;
        this.currentDay = LocalDate.now(clock);
    }

    public synchronized boolean tryAcquire(String authenticatedUserId) {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(currentDay)) {
            currentDay = today;
            dayCount = 0;
            userCounts.clear();
        }
        String hour = ZonedDateTime.now(clock).withZoneSameInstant(zone)
                .truncatedTo(ChronoUnit.HOURS).toInstant().toString();
        String key = authenticatedUserId + "|" + hour;
        int userCount = userCounts.getOrDefault(key, 0);
        if (userCount >= perUserHour || dayCount >= globalDay) return false;
        userCounts.put(key, userCount + 1);
        dayCount++;
        return true;
    }
}

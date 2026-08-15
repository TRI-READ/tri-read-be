package com.triread.api.auth;

import com.triread.api.common.RateLimitException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SignupAttemptService {

    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    private final Map<String, AttemptWindow> attempts = new HashMap<>();
    private final Clock clock;
    private final int shortLimit;
    private final Duration shortWindow;
    private final int dailyLimit;
    private final Duration dailyWindow;

    @Autowired
    public SignupAttemptService(
            @Value("${app.auth.signup-rate-limit.short-limit:5}") int shortLimit,
            @Value("${app.auth.signup-rate-limit.short-window:10m}") Duration shortWindow,
            @Value("${app.auth.signup-rate-limit.daily-limit:10}") int dailyLimit,
            @Value("${app.auth.signup-rate-limit.daily-window:24h}") Duration dailyWindow
    ) {
        this(Clock.systemUTC(), shortLimit, shortWindow, dailyLimit, dailyWindow);
    }

    SignupAttemptService(
            Clock clock,
            int shortLimit,
            Duration shortWindow,
            int dailyLimit,
            Duration dailyWindow
    ) {
        this.clock = clock;
        this.shortLimit = shortLimit;
        this.shortWindow = shortWindow;
        this.dailyLimit = dailyLimit;
        this.dailyWindow = dailyWindow;
    }

    public synchronized void checkAndRecord(String clientAddress) {
        Instant now = clock.instant();
        String address = normalize(clientAddress);
        removeExpiredAddresses(now);

        AttemptWindow current = refresh(attempts.get(address), now);
        long retryAfterSeconds = retryAfterSeconds(current, now);
        if (retryAfterSeconds > 0) {
            throw new RateLimitException(
                    "TOO_MANY_SIGNUP_ATTEMPTS",
                    "Too many signup attempts. Please try again later.",
                    retryAfterSeconds
            );
        }

        if (!attempts.containsKey(address) && attempts.size() >= MAX_TRACKED_ADDRESSES) {
            throw new RateLimitException(
                    "SIGNUP_RATE_LIMIT_BUSY",
                    "Signup is temporarily unavailable. Please try again later.",
                    60
            );
        }

        attempts.put(address, new AttemptWindow(
                current.shortCount() + 1,
                current.shortExpiresAt(),
                current.dailyCount() + 1,
                current.dailyExpiresAt()
        ));
    }

    private AttemptWindow refresh(AttemptWindow current, Instant now) {
        if (current == null) {
            return new AttemptWindow(
                    0,
                    now.plus(shortWindow),
                    0,
                    now.plus(dailyWindow)
            );
        }

        int shortCount = current.shortCount();
        Instant shortExpiresAt = current.shortExpiresAt();
        if (!shortExpiresAt.isAfter(now)) {
            shortCount = 0;
            shortExpiresAt = now.plus(shortWindow);
        }

        int dailyCount = current.dailyCount();
        Instant dailyExpiresAt = current.dailyExpiresAt();
        if (!dailyExpiresAt.isAfter(now)) {
            dailyCount = 0;
            dailyExpiresAt = now.plus(dailyWindow);
        }

        return new AttemptWindow(shortCount, shortExpiresAt, dailyCount, dailyExpiresAt);
    }

    private long retryAfterSeconds(AttemptWindow current, Instant now) {
        long retryAfterSeconds = 0;
        if (current.shortCount() >= shortLimit) {
            retryAfterSeconds = secondsUntil(current.shortExpiresAt(), now);
        }
        if (current.dailyCount() >= dailyLimit) {
            retryAfterSeconds = Math.max(
                    retryAfterSeconds,
                    secondsUntil(current.dailyExpiresAt(), now)
            );
        }
        return retryAfterSeconds;
    }

    private long secondsUntil(Instant expiresAt, Instant now) {
        return Math.max(1, Duration.between(now, expiresAt).getSeconds());
    }

    private void removeExpiredAddresses(Instant now) {
        if (attempts.size() < MAX_TRACKED_ADDRESSES) {
            return;
        }
        attempts.entrySet().removeIf(entry -> !entry.getValue().dailyExpiresAt().isAfter(now));
    }

    private String normalize(String clientAddress) {
        if (clientAddress == null || clientAddress.isBlank()) {
            return "unknown";
        }
        return clientAddress.trim();
    }

    private record AttemptWindow(
            int shortCount,
            Instant shortExpiresAt,
            int dailyCount,
            Instant dailyExpiresAt
    ) {
    }
}

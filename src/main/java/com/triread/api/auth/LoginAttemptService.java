package com.triread.api.auth;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxFailures;
    private final Duration window;

    @Autowired
    public LoginAttemptService(
            @Value("${app.auth.login-rate-limit.max-failures:10}") int maxFailures,
            @Value("${app.auth.login-rate-limit.window:10m}") Duration window
    ) {
        this(Clock.systemUTC(), maxFailures, window);
    }

    LoginAttemptService(Clock clock, int maxFailures, Duration window) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
    }

    public void assertAllowed(String clientAddress, String loginName) {
        String key = key(clientAddress, loginName);
        AttemptWindow current = attempts.get(key);
        if (current == null) {
            return;
        }
        if (current.expiresAt().isBefore(clock.instant())) {
            attempts.remove(key, current);
            return;
        }
        if (current.failures() >= maxFailures) {
            throw rateLimited();
        }
    }

    public void recordFailure(String clientAddress, String loginName) {
        Instant now = clock.instant();
        removeExpired(now);
        String key = key(clientAddress, loginName);
        if (!attempts.containsKey(key) && attempts.size() >= MAX_TRACKED_KEYS) {
            throw rateLimited();
        }
        attempts.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) {
                return new AttemptWindow(1, now.plus(window), normalizeAddress(clientAddress),
                        normalizeLogin(loginName));
            }
            return new AttemptWindow(current.failures() + 1, current.expiresAt(),
                    current.clientAddress(), current.loginName());
        });
    }

    public void recordSuccess(String clientAddress, String loginName) {
        attempts.remove(key(clientAddress, loginName));
    }

    public List<LoginLockSummary> getLockedAttempts() {
        Instant now = clock.instant();
        attempts.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        return attempts.values().stream()
                .filter(attempt -> attempt.failures() >= maxFailures)
                .map(attempt -> new LoginLockSummary(attempt.loginName(),
                        maskAddress(attempt.clientAddress()), attempt.failures(), attempt.expiresAt()))
                .sorted((left, right) -> right.expiresAt().compareTo(left.expiresAt()))
                .toList();
    }

    public int clearLogin(String loginName) {
        String normalized = normalizeLogin(loginName);
        int before = attempts.size();
        attempts.entrySet().removeIf(entry -> entry.getValue().loginName().equals(normalized));
        return before - attempts.size();
    }

    private void removeExpired(Instant now) {
        if (attempts.size() < 100) {
            return;
        }
        attempts.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String key(String clientAddress, String loginName) {
        return normalizeAddress(clientAddress) + ':' + normalizeLogin(loginName);
    }

    private String normalizeAddress(String clientAddress) {
        return clientAddress == null ? "unknown" : clientAddress;
    }

    private String normalizeLogin(String loginName) {
        return loginName == null ? "" : loginName.trim().toLowerCase(Locale.ROOT);
    }

    private String maskAddress(String address) {
        if (address == null || address.isBlank() || "unknown".equals(address)) return "unknown";
        if (address.contains(".")) {
            String[] parts = address.split("\\.");
            return parts.length == 4 ? parts[0] + "." + parts[1] + ".***.***" : "masked";
        }
        return address.contains(":") ? address.substring(0, Math.min(4, address.length())) + ":***" : "masked";
    }

    private ApiException rateLimited() {
        return new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "TOO_MANY_LOGIN_ATTEMPTS",
                "Too many failed login attempts. Try again later."
        );
    }

    private record AttemptWindow(int failures, Instant expiresAt, String clientAddress,
                                 String loginName) {}
    public record LoginLockSummary(String loginName, String clientAddress,
                                   int failures, Instant expiresAt) {}
}

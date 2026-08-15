package com.triread.api.auth;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final int ADDRESS_FAILURE_MULTIPLIER = 5;

    private final Map<String, AttemptWindow> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, AttemptWindow> addressAttempts = new ConcurrentHashMap<>();
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
        Instant now = clock.instant();
        assertWindowAllowed(loginAttempts, normalizeLogin(loginName), maxFailures, now);
        assertWindowAllowed(
                addressAttempts,
                normalizeAddress(clientAddress),
                maxFailures * ADDRESS_FAILURE_MULTIPLIER,
                now
        );
    }

    private void assertWindowAllowed(Map<String, AttemptWindow> attempts,
                                     String key,
                                     int failureLimit,
                                     Instant now) {
        AttemptWindow current = attempts.get(key);
        if (current == null) {
            return;
        }
        if (current.expiresAt().isBefore(now)) {
            attempts.remove(key, current);
            return;
        }
        if (current.failures() >= failureLimit) {
            throw rateLimited();
        }
    }

    public void recordFailure(String clientAddress, String loginName) {
        Instant now = clock.instant();
        if (trackedKeyCount() >= 100) {
            removeExpired(now);
        }
        recordAttempt(
                loginAttempts,
                normalizeLogin(loginName),
                clientAddress,
                loginName,
                now
        );
        recordAttempt(
                addressAttempts,
                normalizeAddress(clientAddress),
                clientAddress,
                "",
                now
        );
    }

    public void recordSuccess(String clientAddress, String loginName) {
        loginAttempts.remove(normalizeLogin(loginName));
    }

    public List<LoginLockSummary> getLockedAttempts() {
        Instant now = clock.instant();
        removeExpired(now);

        List<LoginLockSummary> lockedAttempts = new ArrayList<>();
        for (AttemptWindow attempt : loginAttempts.values()) {
            if (attempt.failures() >= maxFailures) {
                lockedAttempts.add(toSummary(attempt));
            }
        }
        lockedAttempts.sort(Comparator.comparing(LoginLockSummary::expiresAt).reversed());
        return lockedAttempts;
    }

    public int clearLogin(String loginName) {
        String normalized = normalizeLogin(loginName);
        return loginAttempts.remove(normalized) == null ? 0 : 1;
    }

    private void removeExpired(Instant now) {
        removeExpired(loginAttempts, now);
        removeExpired(addressAttempts, now);
    }

    private void removeExpired(Map<String, AttemptWindow> attempts, Instant now) {
        attempts.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private void recordAttempt(Map<String, AttemptWindow> attempts,
                               String key,
                               String clientAddress,
                               String loginName,
                               Instant now) {
        if (!attempts.containsKey(key) && trackedKeyCount() >= MAX_TRACKED_KEYS) {
            throw rateLimited();
        }
        attempts.compute(key, (ignored, current) ->
                nextAttempt(current, clientAddress, loginName, now));
    }

    private int trackedKeyCount() {
        return loginAttempts.size() + addressAttempts.size();
    }

    private AttemptWindow nextAttempt(
            AttemptWindow current,
            String clientAddress,
            String loginName,
            Instant now
    ) {
        if (current == null || current.expiresAt().isBefore(now)) {
            return new AttemptWindow(
                    1,
                    now.plus(window),
                    normalizeAddress(clientAddress),
                    normalizeLogin(loginName)
            );
        }
        return new AttemptWindow(
                current.failures() + 1,
                current.expiresAt(),
                current.clientAddress(),
                current.loginName()
        );
    }

    private LoginLockSummary toSummary(AttemptWindow attempt) {
        return new LoginLockSummary(
                attempt.loginName(),
                maskAddress(attempt.clientAddress()),
                attempt.failures(),
                attempt.expiresAt()
        );
    }

    private String normalizeAddress(String clientAddress) {
        return clientAddress == null ? "unknown" : clientAddress;
    }

    private String normalizeLogin(String loginName) {
        return loginName == null ? "" : loginName.trim().toLowerCase(Locale.ROOT);
    }

    private String maskAddress(String address) {
        if (address == null || address.isBlank() || "unknown".equals(address)) {
            return "unknown";
        }
        if (address.contains(".")) {
            String[] parts = address.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".***.***";
            }
            return "masked";
        }
        if (address.contains(":")) {
            int visibleLength = Math.min(4, address.length());
            return address.substring(0, visibleLength) + ":***";
        }
        return "masked";
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

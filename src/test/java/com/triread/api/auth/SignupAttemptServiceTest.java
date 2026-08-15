package com.triread.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triread.api.common.RateLimitException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SignupAttemptServiceTest {

    @Test
    void blocksSixthAttemptWithinTenMinutes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-14T00:00:00Z"));
        SignupAttemptService service = new SignupAttemptService(
                clock,
                5,
                Duration.ofMinutes(10),
                10,
                Duration.ofHours(24)
        );

        for (int attempt = 0; attempt < 5; attempt++) {
            service.checkAndRecord("192.0.2.1");
        }

        assertThatThrownBy(() -> service.checkAndRecord("192.0.2.1"))
                .isInstanceOfSatisfying(RateLimitException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("TOO_MANY_SIGNUP_ATTEMPTS");
                    assertThat(exception.getRetryAfterSeconds()).isEqualTo(600);
                });
    }

    @Test
    void blocksEleventhAttemptWithinOneDay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-14T00:00:00Z"));
        SignupAttemptService service = new SignupAttemptService(
                clock,
                5,
                Duration.ofMinutes(10),
                10,
                Duration.ofHours(24)
        );

        for (int attempt = 0; attempt < 5; attempt++) {
            service.checkAndRecord("192.0.2.1");
        }
        clock.advance(Duration.ofMinutes(10));
        for (int attempt = 0; attempt < 5; attempt++) {
            service.checkAndRecord("192.0.2.1");
        }

        assertThatThrownBy(() -> service.checkAndRecord("192.0.2.1"))
                .isInstanceOfSatisfying(RateLimitException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("TOO_MANY_SIGNUP_ATTEMPTS");
                    assertThat(exception.getRetryAfterSeconds())
                            .isEqualTo(Duration.ofHours(24).minusMinutes(10).getSeconds());
                });
    }

    @Test
    void tracksEachAddressSeparately() {
        SignupAttemptService service = new SignupAttemptService(
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC),
                1,
                Duration.ofMinutes(10),
                10,
                Duration.ofHours(24)
        );

        service.checkAndRecord("192.0.2.1");
        service.checkAndRecord("192.0.2.2");

        assertThatThrownBy(() -> service.checkAndRecord("192.0.2.1"))
                .isInstanceOf(RateLimitException.class);
    }

    private static class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}

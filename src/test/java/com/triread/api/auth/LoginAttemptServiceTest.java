package com.triread.api.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

class LoginAttemptServiceTest {

    private final LoginAttemptService service = new LoginAttemptService(
            Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC),
            3,
            Duration.ofMinutes(10)
    );

    @Test
    void blocksLoginAfterConfiguredFailures() {
        service.recordFailure("203.0.113.1", "reader");
        service.recordFailure("203.0.113.1", "reader");
        service.recordFailure("203.0.113.1", "reader");

        assertThatThrownBy(() -> service.assertAllowed("203.0.113.1", "reader"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
    }

    @Test
    void successfulLoginClearsFailures() {
        service.recordFailure("203.0.113.1", "reader");
        service.recordFailure("203.0.113.1", "reader");
        service.recordFailure("203.0.113.1", "reader");
        service.recordSuccess("203.0.113.1", "reader");

        assertThatCode(() -> service.assertAllowed("203.0.113.1", "reader"))
                .doesNotThrowAnyException();
    }

    @Test
    void springContextCreatesServiceWithConfiguredConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context,
                    "app.auth.login-rate-limit.max-failures=5",
                    "app.auth.login-rate-limit.window=15m"
            );
            context.register(LoginAttemptService.class);
            context.refresh();

            assertThatCode(() -> context.getBean(LoginAttemptService.class))
                    .doesNotThrowAnyException();
        }
    }
}

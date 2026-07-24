package com.triread.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscordNotificationServiceTest {
    private static final String WEBHOOK_URL =
            "https://discord.com/api/webhooks/test-id/test-token";
    @Mock DiscordWebhookClient webhookClient;
    private DiscordNotificationProperties properties;
    private DiscordNotificationService service;

    @BeforeEach
    void setUp() {
        properties = new DiscordNotificationProperties();
        properties.setEnabled(true);
        properties.setWebhookUrl(WEBHOOK_URL);
        properties.setCooldown(Duration.ofMinutes(15));
        properties.setEnvironment("test");
        service = new DiscordNotificationService(
                properties,
                webhookClient,
                Clock.fixed(Instant.parse("2026-07-23T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void sendsOnlyOneFailureWithinCooldown() {
        assertThat(service.notifyFailure("QUIZ_GENERATION", "failed", "detail")).isTrue();
        assertThat(service.notifyFailure("QUIZ_GENERATION", "failed", "detail")).isFalse();

        verify(webhookClient, times(1)).send(eq(WEBHOOK_URL), anyString());
    }

    @Test
    void skipsAutomaticNotificationWhenDisabled() {
        properties.setEnabled(false);

        assertThat(service.notifyFailure("QUIZ_GENERATION", "failed", "detail")).isFalse();

        verify(webhookClient, never()).send(anyString(), anyString());
    }

    @Test
    void rejectsManualTestWhenWebhookIsNotConfigured() {
        properties.setWebhookUrl("");

        assertThatThrownBy(() -> service.sendTest("admin"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("DISCORD_NOTIFICATION_DISABLED");
    }

    @Test
    void sendsManualTestWithoutUsingFailureCooldown() {
        DiscordNotificationService.SendResult result = service.sendTest("admin");

        assertThat(result.delivered()).isTrue();
        verify(webhookClient).send(
                eq(WEBHOOK_URL),
                contains("Manual operations notification test")
        );
    }
}

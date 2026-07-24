package com.triread.api.notification;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DiscordNotificationService {
    private static final Logger log = LoggerFactory.getLogger(DiscordNotificationService.class);
    private static final int MAX_CONTENT_LENGTH = 1900;
    private final DiscordNotificationProperties properties;
    private final DiscordWebhookClient webhookClient;
    private final Clock clock;
    private final Map<String, Instant> lastSentAt = new ConcurrentHashMap<>();

    public DiscordNotificationService(DiscordNotificationProperties properties,
                                      DiscordWebhookClient webhookClient,
                                      Clock clock) {
        this.properties = properties;
        this.webhookClient = webhookClient;
        this.clock = clock;
    }

    public NotificationStatus status() {
        return new NotificationStatus(
                properties.isEnabled(),
                isConfigured(),
                cooldown().toSeconds(),
                environment()
        );
    }

    public SendResult sendTest(String actorName) {
        ensureAvailable();
        String content = format(
                "TEST",
                "Manual operations notification test",
                "Requested by: " + safe(actorName) + "\nDiscord operations alert is configured."
        );
        try {
            webhookClient.send(properties.getWebhookUrl().trim(), content);
            return new SendResult(true, "Discord test notification was delivered.");
        } catch (RuntimeException exception) {
            log.warn("Discord test notification failed: {}", exception.getClass().getSimpleName());
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "DISCORD_NOTIFICATION_FAILED",
                    "Discord test notification could not be delivered."
            );
        }
    }

    public synchronized boolean notifyFailure(String key, String title, String detail) {
        if (!properties.isEnabled() || !isConfigured()) return false;
        Instant now = clock.instant();
        Instant previous = lastSentAt.get(key);
        if (previous != null && previous.plus(cooldown()).isAfter(now)) return false;

        try {
            webhookClient.send(
                    properties.getWebhookUrl().trim(),
                    format("FAILED", title, detail)
            );
            lastSentAt.put(key, now);
            return true;
        } catch (RuntimeException exception) {
            log.warn(
                    "Discord failure notification could not be delivered: {}",
                    exception.getClass().getSimpleName()
            );
            return false;
        }
    }

    private void ensureAvailable() {
        if (!properties.isEnabled() || !isConfigured()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DISCORD_NOTIFICATION_DISABLED",
                    "Discord operations notification is not configured."
            );
        }
    }

    private boolean isConfigured() {
        String webhookUrl = properties.getWebhookUrl();
        if (webhookUrl == null) return false;
        String normalized = webhookUrl.trim();
        return normalized.startsWith("https://discord.com/api/webhooks/")
                || normalized.startsWith("https://discordapp.com/api/webhooks/");
    }

    private Duration cooldown() {
        Duration configured = properties.getCooldown();
        return configured == null || configured.isNegative()
                ? Duration.ofMinutes(15)
                : configured;
    }

    private String environment() {
        String configured = properties.getEnvironment();
        return configured == null || configured.isBlank() ? "unknown" : configured.trim();
    }

    private String format(String status, String title, String detail) {
        String content = """
                [TRI:READ][%s][%s]
                %s
                %s
                Time: %s
                """.formatted(
                environment().toUpperCase(),
                status,
                safe(title),
                safe(detail),
                clock.instant()
        );
        return content.length() <= MAX_CONTENT_LENGTH
                ? content
                : content.substring(0, MAX_CONTENT_LENGTH - 3) + "...";
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) return "-";
        return value.replace("@", "@\u200B").trim();
    }

    public record NotificationStatus(boolean enabled, boolean configured,
                                     long cooldownSeconds, String environment) {}

    public record SendResult(boolean delivered, String message) {}
}

package com.triread.api.notification;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DiscordWebhookClient {
    private final RestClient restClient;

    public DiscordWebhookClient() {
        this(RestClient.create());
    }

    DiscordWebhookClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public void send(String webhookUrl, String content) {
        restClient.post()
                .uri(webhookUrl)
                .body(Map.of("content", content))
                .retrieve()
                .toBodilessEntity();
    }
}

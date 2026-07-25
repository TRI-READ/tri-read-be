package com.triread.api.notification;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class DiscordWebhookClientTest {

    @Test
    void createsWithoutAnAutoConfiguredRestClientBuilder() {
        assertThatCode(DiscordWebhookClient::new).doesNotThrowAnyException();
    }
}

package com.triread.api.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void healthReturnsServiceStatusAndKoreanDate() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-07T12:00:00Z"), ZoneId.of("Asia/Seoul"));
        HealthController controller = new HealthController(clock);

        HealthController.HealthResponse response = controller.health();

        assertThat(response.status()).isEqualTo("ok");
        assertThat(response.timeZone()).isEqualTo("Asia/Seoul");
        assertThat(response.today().toString()).isEqualTo("2026-07-07");
    }
}


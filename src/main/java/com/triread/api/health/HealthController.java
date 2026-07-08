package com.triread.api.health;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final Clock clock;

    public HealthController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse(
                "ok",
                clock.getZone().getId(),
                LocalDate.now(clock),
                Instant.now(clock)
        );
    }

    public record HealthResponse(
            String status,
            String timeZone,
            LocalDate today,
            Instant checkedAt
    ) {
    }
}


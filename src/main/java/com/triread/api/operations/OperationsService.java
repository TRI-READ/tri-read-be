package com.triread.api.operations;

import com.triread.api.auth.LoginAttemptService;
import com.triread.api.generation.QuizGenerationProperties;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsService {
    private final OperationsMapper mapper;
    private final LoginAttemptService loginAttemptService;
    private final QuizGenerationProperties properties;
    private final Clock clock;
    private final String version;

    public OperationsService(OperationsMapper mapper, LoginAttemptService loginAttemptService,
                             QuizGenerationProperties properties, Clock clock,
                             @Value("${app.version:dev}") String version) {
        this.mapper = mapper;
        this.loginAttemptService = loginAttemptService;
        this.properties = properties;
        this.clock = clock;
        this.version = version;
    }

    @Transactional(readOnly = true)
    public OperationsData.Summary summary() {
        LocalDate today = LocalDate.now(clock);
        Instant from = today.atStartOfDay(clock.getZone()).toInstant();
        Instant until = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        Instant qualityFrom = today.minusDays(6).atStartOfDay(clock.getZone()).toInstant();
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        Instant startedAt = clock.instant().minusSeconds(uptime);
        return new OperationsData.Summary(
                "UP", "UP", mapper.databaseSizeBytes(), uptime, version, startedAt,
                mapper.aiStats(from, until), mapper.aiErrors(from, until),
                mapper.qualityStats(qualityFrom),
                mapper.inventory(today, today.plusDays(6),
                        Math.max(1, properties.getVariantsPerDate())),
                mapper.recentFailures(10), mapper.recentAdminActions(10),
                loginAttemptService.getLockedAttempts().size(),
                mapper.lastEvent("QUIZ_SCHEDULER"), nextRun(properties.getCron()),
                mapper.lastEvent("DB_BACKUP"), mapper.countGroundedBriefs(),
                mapper.countGroundedSources());
    }

    public long startEvent(String eventType, String message) {
        OperationsData.EventInsert event =
                new OperationsData.EventInsert(eventType, "STARTED", message);
        mapper.insertEvent(event);
        return event.getId();
    }

    public void completeEvent(long eventId, boolean success, String message) {
        mapper.completeEvent(eventId, success ? "SUCCESS" : "FAILED", message, clock.instant());
    }

    private Instant nextRun(String cron) {
        ZonedDateTime next = CronExpression.parse(cron).next(ZonedDateTime.now(clock));
        return next == null ? null : next.toInstant();
    }
}

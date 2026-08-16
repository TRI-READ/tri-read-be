package com.triread.api.operations;

import com.triread.api.auth.LoginAttemptService;
import com.triread.api.generation.AiApiUsageService;
import com.triread.api.generation.QuizGenerationProperties;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsService {
    private static final int RECENT_ITEM_LIMIT = 10;

    private final OperationsMapper mapper;
    private final LoginAttemptService loginAttemptService;
    private final AiApiUsageService apiUsageService;
    private final QuizGenerationProperties properties;
    private final Clock clock;
    private final String version;

    public OperationsService(OperationsMapper mapper, LoginAttemptService loginAttemptService,
                             AiApiUsageService apiUsageService,
                             QuizGenerationProperties properties, Clock clock,
                             @Value("${app.version:dev}") String version) {
        this.mapper = mapper;
        this.loginAttemptService = loginAttemptService;
        this.apiUsageService = apiUsageService;
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

        OperationsData.AiStats aiStats = mapper.aiStats(from, until);
        List<OperationsData.ErrorCount> aiErrors = mapper.aiErrors(from, until);
        OperationsData.QualityStats qualityStats = mapper.qualityStats(qualityFrom);
        List<OperationsData.InventoryRow> inventory = mapper.inventory(
                today,
                today.plusDays(6),
                Math.max(1, properties.getVariantsPerDate())
        );
        List<OperationsData.FailureRow> recentFailures =
                mapper.recentFailures(RECENT_ITEM_LIMIT);
        List<OperationsData.AuditRow> recentAdminActions =
                mapper.recentAdminActions(RECENT_ITEM_LIMIT);
        int lockedLoginAttempts = loginAttemptService.getLockedAttempts().size();
        OperationsData.OperationEventRow lastSchedulerRun =
                mapper.lastEvent("QUIZ_SCHEDULER");
        OperationsData.OperationEventRow lastBackup = mapper.lastEvent("DB_BACKUP");

        return new OperationsData.Summary(
                "UP", "UP", mapper.databaseSizeBytes(), uptime, version, startedAt,
                aiStats, aiErrors, apiUsageService.rateLimitRetryAt(), qualityStats, inventory,
                recentFailures, recentAdminActions, lockedLoginAttempts,
                lastSchedulerRun, nextRun(properties.getCron()),
                lastBackup, mapper.countGroundedBriefs(),
                mapper.countGroundedSources());
    }

    public long startEvent(String eventType, String message) {
        OperationsData.EventInsert event =
                new OperationsData.EventInsert(eventType, "STARTED", message);
        mapper.insertEvent(event);
        return event.getId();
    }

    public void completeEvent(long eventId, boolean success, String message) {
        String status = success ? "SUCCESS" : "FAILED";
        mapper.completeEvent(eventId, status, message, clock.instant());
    }

    private Instant nextRun(String cron) {
        ZonedDateTime next = CronExpression.parse(cron).next(ZonedDateTime.now(clock));
        return next == null ? null : next.toInstant();
    }
}

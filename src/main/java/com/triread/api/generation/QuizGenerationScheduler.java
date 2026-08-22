package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;
import com.triread.api.common.ApiException;
import com.triread.api.operations.OperationsService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class QuizGenerationScheduler {
    private static final Logger log = LoggerFactory.getLogger(QuizGenerationScheduler.class);
    private final QuizGenerationService generationService;
    private final AdminQuizService adminQuizService;
    private final QuizGenerationProperties properties;
    private final OperationsService operationsService;
    private final AiApiUsageService apiUsageService;
    private final Clock clock;

    public QuizGenerationScheduler(QuizGenerationService generationService,
                                   AdminQuizService adminQuizService,
                                   QuizGenerationProperties properties,
                                   OperationsService operationsService,
                                   AiApiUsageService apiUsageService,
                                   Clock clock) {
        this.generationService = generationService;
        this.adminQuizService = adminQuizService;
        this.properties = properties;
        this.operationsService = operationsService;
        this.apiUsageService = apiUsageService;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.quiz-generation.cron}", zone = "${app.time-zone}")
    public void replenishInventory() {
        if (!properties.isEnabled()) return;
        runInventoryJob("PRIMARY");
    }

    private void runInventoryJob(String trigger) {
        long eventId = operationsService.startEvent(
                "QUIZ_SCHEDULER", trigger + " inventory replenishment started");
        Instant retryAt = apiUsageService.rateLimitRetryAt();
        if (retryAt != null) {
            String message = trigger + " inventory replenishment skipped: Gemini cooldown until "
                    + retryAt;
            log.info(message);
            operationsService.completeEvent(eventId, true, message);
            return;
        }
        int jobsStarted = 0;
        int recycled = 0;
        int failed = 0;
        boolean success = false;
        String resultMessage = null;
        int inventoryDays = Math.max(1, properties.getInventoryDays());
        int maxJobsPerRun = Math.max(1, properties.getMaxJobsPerRun());
        List<LocalDate> targetDates = upcomingDates(LocalDate.now(clock), inventoryDays);
        int variantsPerDate = Math.max(1, properties.getVariantsPerDate());
        try {
            for (LocalDate targetDate : targetDates) {
                int activeCount = adminQuizService.countActiveQuizSets(targetDate);
                while (activeCount < variantsPerDate) {
                    if (adminQuizService.recycleUnusedPublishedQuiz(targetDate)) {
                        activeCount++;
                        recycled++;
                        continue;
                    }
                    if (jobsStarted >= maxJobsPerRun) {
                        success = failed == 0;
                        return;
                    }
                    jobsStarted++;
                    try {
                        generationService.generate(targetDate);
                        activeCount++;
                    } catch (RuntimeException exception) {
                        if (isDailyLimit(exception)) {
                log.info("Daily Gemini API call limit reached; stopping inventory replenishment");
                success = true;
                resultMessage = trigger
                        + " inventory replenishment stopped: daily Gemini API call limit reached";
                            return;
                        }
                        if (isRateLimited(exception)) {
                            failed++;
                            retryAt = apiUsageService.rateLimitRetryAt();
                            resultMessage = trigger
                                    + " inventory replenishment stopped: Gemini rate limit reached"
                                    + (retryAt == null ? "" : ", retry after " + retryAt);
                            log.warn(resultMessage);
                            return;
                        }
                        log.error("Scheduled quiz generation failed for {}", targetDate, exception);
                        failed++;
                        break;
                    }
                }
            }
            success = failed == 0;
        } finally {
            operationsService.completeEvent(eventId, success,
                    resultMessage != null ? resultMessage
                            : trigger + " inventory replenishment finished: generated="
                                    + jobsStarted + ", recycled=" + recycled + ", failed=" + failed);
        }
    }

    @Scheduled(cron = "${app.quiz-generation.recovery-cron}", zone = "${app.time-zone}")
    public void recoverInventory() {
        if (!properties.isEnabled()) return;
        runInventoryJob("RECOVERY");
    }

    private boolean isDailyLimit(RuntimeException exception) {
        if (!(exception instanceof ApiException apiException)) {
            return false;
        }
        return "QUIZ_GENERATION_API_DAILY_LIMIT_REACHED".equals(apiException.getCode());
    }

    private boolean isRateLimited(RuntimeException exception) {
        return exception instanceof ApiException apiException
                && "GEMINI_RATE_LIMITED".equals(apiException.getCode());
    }

    private List<LocalDate> upcomingDates(LocalDate today, int inventoryDays) {
        return Stream.iterate(today, date -> date.plusDays(1))
                .limit(inventoryDays)
                .toList();
    }
}

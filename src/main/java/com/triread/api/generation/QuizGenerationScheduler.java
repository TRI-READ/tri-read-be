package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;
import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.DayOfWeek;
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
    private final Clock clock;

    public QuizGenerationScheduler(QuizGenerationService generationService,
                                   AdminQuizService adminQuizService,
                                   QuizGenerationProperties properties,
                                   Clock clock) {
        this.generationService = generationService;
        this.adminQuizService = adminQuizService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.quiz-generation.cron}", zone = "${app.time-zone}")
    public void replenishInventory() {
        if (!properties.isEnabled()) return;

        int inventoryDays = Math.max(1, properties.getInventoryDays());
        int maxJobsPerRun = Math.max(1, properties.getMaxJobsPerRun());
        List<LocalDate> targetDates = upcomingWeekdays(LocalDate.now(clock), inventoryDays);
        int variantsPerDate = Math.max(1, properties.getVariantsPerDate());
        int jobsStarted = 0;

        for (LocalDate targetDate : targetDates) {
            int activeCount = adminQuizService.countActiveQuizSets(targetDate);
            while (activeCount < variantsPerDate) {
                if (adminQuizService.recycleUnusedPublishedQuiz(targetDate)) {
                    activeCount++;
                    continue;
                }
                if (jobsStarted >= maxJobsPerRun) return;
                jobsStarted++;
                try {
                    generationService.generate(targetDate);
                    activeCount++;
                } catch (RuntimeException exception) {
                    if (exception instanceof ApiException apiException
                            && "QUIZ_GENERATION_DAILY_LIMIT_REACHED".equals(apiException.getCode())) {
                        log.info("Daily quiz generation budget reached; stopping inventory replenishment");
                        return;
                    }
                    log.error("Scheduled quiz generation failed for {}", targetDate, exception);
                    break;
                }
            }
        }
    }

    @Scheduled(cron = "${app.quiz-generation.recovery-cron}", zone = "${app.time-zone}")
    public void recoverInventory() {
        replenishInventory();
    }

    private List<LocalDate> upcomingWeekdays(LocalDate today, int inventoryDays) {
        return Stream.iterate(today, date -> date.plusDays(1))
                .filter(this::isWeekday)
                .limit(inventoryDays)
                .toList();
    }

    private boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}

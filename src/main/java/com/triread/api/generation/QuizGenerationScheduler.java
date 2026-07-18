package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final int MINIMUM_INVENTORY_DAYS = 3;
    private static final int TARGET_INVENTORY_DAYS = 7;

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

        List<LocalDate> targetDates = upcomingWeekdays(LocalDate.now(clock));
        int variantsPerDate = Math.max(1, properties.getVariantsPerDate());
        Map<LocalDate, Integer> activeCounts = new HashMap<>();
        for (LocalDate targetDate : targetDates) {
            activeCounts.put(targetDate, adminQuizService.countActiveQuizSets(targetDate));
        }

        int consecutiveInventoryDays = 0;
        for (LocalDate targetDate : targetDates) {
            if (activeCounts.get(targetDate) < variantsPerDate) break;
            consecutiveInventoryDays++;
        }
        if (consecutiveInventoryDays >= MINIMUM_INVENTORY_DAYS) return;

        for (LocalDate targetDate : targetDates) {
            int activeCount = activeCounts.get(targetDate);
            while (activeCount < variantsPerDate) {
                if (adminQuizService.recycleUnusedPublishedQuiz(targetDate)) {
                    activeCount++;
                    continue;
                }
                try {
                    generationService.generate(targetDate);
                    activeCount++;
                } catch (RuntimeException exception) {
                    log.error("Scheduled quiz generation failed for {}", targetDate, exception);
                    break;
                }
            }
        }
    }

    private List<LocalDate> upcomingWeekdays(LocalDate today) {
        return Stream.iterate(today, date -> date.plusDays(1))
                .filter(this::isWeekday)
                .limit(TARGET_INVENTORY_DAYS)
                .toList();
    }

    private boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}

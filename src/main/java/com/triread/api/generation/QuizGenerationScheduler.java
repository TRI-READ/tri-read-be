package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        Set<LocalDate> activeDates = new HashSet<>();
        for (LocalDate targetDate : targetDates) {
            if (adminQuizService.hasActiveQuiz(targetDate)) {
                activeDates.add(targetDate);
            }
        }

        int consecutiveInventoryDays = 0;
        for (LocalDate targetDate : targetDates) {
            if (!activeDates.contains(targetDate)) break;
            consecutiveInventoryDays++;
        }
        if (consecutiveInventoryDays >= MINIMUM_INVENTORY_DAYS) return;

        for (LocalDate targetDate : targetDates) {
            if (activeDates.contains(targetDate)) continue;
            try {
                generationService.generate(targetDate);
            } catch (RuntimeException exception) {
                log.error("Scheduled quiz generation failed for {}", targetDate, exception);
            }
        }
    }

    private List<LocalDate> upcomingWeekdays(LocalDate today) {
        return Stream.iterate(today.plusDays(1), date -> date.plusDays(1))
                .filter(this::isWeekday)
                .limit(TARGET_INVENTORY_DAYS)
                .toList();
    }

    private boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}

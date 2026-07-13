package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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
    public void generateNextWeek() {
        if (!properties.isEnabled()) return;

        LocalDate monday = LocalDate.now(clock).plusDays(1)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        for (int offset = 0; offset < 5; offset++) {
            LocalDate targetDate = monday.plusDays(offset);
            if (adminQuizService.hasActiveQuiz(targetDate)) continue;
            try {
                generationService.generate(targetDate);
            } catch (RuntimeException exception) {
                log.error("Scheduled quiz generation failed for {}", targetDate, exception);
            }
        }
    }
}

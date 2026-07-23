package com.triread.api.generation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.triread.api.admin.AdminQuizService;
import com.triread.api.common.ApiException;
import com.triread.api.operations.OperationsService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class QuizGenerationSchedulerTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock MONDAY_CLOCK = Clock.fixed(
            Instant.parse("2026-07-13T03:00:00Z"), SEOUL);

    @Mock QuizGenerationService generationService;
    @Mock AdminQuizService adminQuizService;
    @Mock OperationsService operationsService;
    private QuizGenerationProperties properties;
    private QuizGenerationScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new QuizGenerationProperties();
        properties.setEnabled(true);
        properties.setVariantsPerDate(3);
        properties.setInventoryDays(3);
        properties.setMaxJobsPerRun(3);
        scheduler = new QuizGenerationScheduler(
                generationService, adminQuizService, properties, operationsService, MONDAY_CLOCK);
    }

    @Test
    void doesNothingWhenGenerationIsDisabled() {
        properties.setEnabled(false);

        scheduler.replenishInventory();

        verifyNoInteractions(adminQuizService, generationService);
    }

    @Test
    void keepsInventoryWhenConfiguredWeekdaysAreFull() {
        stubActiveCounts(Map.of(
                LocalDate.of(2026, 7, 13), 3,
                LocalDate.of(2026, 7, 14), 3,
                LocalDate.of(2026, 7, 15), 3));

        scheduler.replenishInventory();

        verify(generationService, never()).generate(any());
    }

    @Test
    void startsAtMostThreeGenerationJobsPerRun() {
        stubActiveCounts(Map.of(
                LocalDate.of(2026, 7, 13), 3,
                LocalDate.of(2026, 7, 14), 3));

        scheduler.replenishInventory();

        verify(generationService, times(3)).generate(LocalDate.of(2026, 7, 15));
        verify(generationService, never()).generate(LocalDate.of(2026, 7, 16));
    }

    @Test
    void failedJobStillConsumesRunBudget() {
        stubActiveCounts(Map.of());
        LocalDate failedDate = LocalDate.of(2026, 7, 13);
        doThrow(new IllegalStateException("generation failed"))
                .when(generationService).generate(failedDate);

        scheduler.replenishInventory();

        verify(generationService).generate(failedDate);
        verify(generationService, times(2)).generate(LocalDate.of(2026, 7, 14));
        verify(generationService, never()).generate(LocalDate.of(2026, 7, 15));
    }

    @Test
    void stopsImmediatelyWhenDailyBudgetIsExhausted() {
        stubActiveCounts(Map.of());
        LocalDate firstDate = LocalDate.of(2026, 7, 13);
        doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                "QUIZ_GENERATION_DAILY_LIMIT_REACHED", "Daily limit reached"))
                .when(generationService).generate(firstDate);

        scheduler.replenishInventory();

        verify(generationService).generate(firstDate);
        verify(generationService, never()).generate(LocalDate.of(2026, 7, 14));
    }

    @Test
    void reusesUnassignedPublishedSetsBeforeGenerating() {
        properties.setVariantsPerDate(1);
        stubActiveCounts(Map.of());
        when(adminQuizService.recycleUnusedPublishedQuiz(any())).thenReturn(true);

        scheduler.replenishInventory();

        verify(adminQuizService).recycleUnusedPublishedQuiz(LocalDate.of(2026, 7, 13));
        verify(generationService, never()).generate(any());
    }

    @Test
    void startsFromMondayWhenRunOnWeekend() {
        Clock saturdayClock = Clock.fixed(
                Instant.parse("2026-07-18T03:00:00Z"), SEOUL);
        scheduler = new QuizGenerationScheduler(
                generationService, adminQuizService, properties, operationsService, saturdayClock);
        stubActiveCounts(Map.of());

        scheduler.replenishInventory();

        verify(generationService, times(3)).generate(LocalDate.of(2026, 7, 20));
        verify(generationService, never()).generate(LocalDate.of(2026, 7, 21));
        verify(generationService, never()).generate(LocalDate.of(2026, 7, 18));
        verify(generationService, never()).generate(LocalDate.of(2026, 7, 19));
    }

    @Test
    void onlyChecksConfiguredInventoryDays() {
        properties.setInventoryDays(2);
        stubActiveCounts(Map.of(
                LocalDate.of(2026, 7, 13), 3,
                LocalDate.of(2026, 7, 14), 3));

        scheduler.replenishInventory();

        verify(adminQuizService, never()).countActiveQuizSets(LocalDate.of(2026, 7, 15));
        verify(generationService, never()).generate(any());
    }

    @Test
    void recoveryScheduleUsesSameInventoryPolicy() {
        stubActiveCounts(Map.of(
                LocalDate.of(2026, 7, 13), 3,
                LocalDate.of(2026, 7, 14), 3,
                LocalDate.of(2026, 7, 15), 3));

        scheduler.recoverInventory();

        verify(generationService, never()).generate(any());
    }

    private void stubActiveCounts(Map<LocalDate, Integer> activeCounts) {
        when(adminQuizService.countActiveQuizSets(any()))
                .thenAnswer(invocation -> activeCounts.getOrDefault(invocation.getArgument(0), 0));
    }
}

package com.triread.api.generation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.triread.api.admin.AdminQuizService;
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

@ExtendWith(MockitoExtension.class)
class QuizGenerationSchedulerTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock MONDAY_CLOCK = Clock.fixed(
            Instant.parse("2026-07-13T03:00:00Z"), SEOUL);

    @Mock QuizGenerationService generationService;
    @Mock AdminQuizService adminQuizService;
    private QuizGenerationProperties properties;
    private QuizGenerationScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new QuizGenerationProperties();
        properties.setEnabled(true);
        properties.setVariantsPerDate(3);
        scheduler = new QuizGenerationScheduler(
                generationService, adminQuizService, properties, MONDAY_CLOCK);
    }

    @Test
    void doesNothingWhenGenerationIsDisabled() {
        properties.setEnabled(false);

        scheduler.replenishInventory();

        verifyNoInteractions(adminQuizService, generationService);
    }

    @Test
    void keepsInventoryWhenAtLeastThreeConsecutiveWeekdaysExist() {
        stubActiveCounts(Map.of(
                LocalDate.of(2026, 7, 13), 3,
                LocalDate.of(2026, 7, 14), 3,
                LocalDate.of(2026, 7, 15), 3));

        scheduler.replenishInventory();

        verify(generationService, never()).generate(any());
    }

    @Test
    void replenishesMissingInventoryToSevenWeekdays() {
        stubActiveCounts(Map.of(
                LocalDate.of(2026, 7, 13), 3,
                LocalDate.of(2026, 7, 14), 3));

        scheduler.replenishInventory();

        verify(generationService, times(3)).generate(LocalDate.of(2026, 7, 15));
        verify(generationService, times(3)).generate(LocalDate.of(2026, 7, 16));
        verify(generationService, times(3)).generate(LocalDate.of(2026, 7, 17));
        verify(generationService, times(3)).generate(LocalDate.of(2026, 7, 20));
        verify(generationService, times(3)).generate(LocalDate.of(2026, 7, 21));
        verify(generationService, never()).generate(LocalDate.of(2026, 7, 22));
        verify(generationService, never()).generate(LocalDate.of(2026, 7, 18));
        verify(generationService, never()).generate(LocalDate.of(2026, 7, 19));
    }

    @Test
    void continuesAfterOneDateFailsToGenerate() {
        stubActiveCounts(Map.of());
        LocalDate failedDate = LocalDate.of(2026, 7, 13);
        doThrow(new IllegalStateException("generation failed"))
                .when(generationService).generate(failedDate);

        scheduler.replenishInventory();

        verify(generationService).generate(failedDate);
        verify(generationService, times(3)).generate(LocalDate.of(2026, 7, 21));
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
                generationService, adminQuizService, properties, saturdayClock);
        stubActiveCounts(Map.of());

        scheduler.replenishInventory();

        verify(generationService, times(3)).generate(LocalDate.of(2026, 7, 20));
        verify(generationService, never()).generate(LocalDate.of(2026, 7, 18));
        verify(generationService, never()).generate(LocalDate.of(2026, 7, 19));
    }

    private void stubActiveCounts(Map<LocalDate, Integer> activeCounts) {
        when(adminQuizService.countActiveQuizSets(any()))
                .thenAnswer(invocation -> activeCounts.getOrDefault(invocation.getArgument(0), 0));
    }
}

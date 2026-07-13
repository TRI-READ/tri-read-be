package com.triread.api.generation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.triread.api.admin.AdminQuizService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
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
        stubActiveDates(Set.of(
                LocalDate.of(2026, 7, 14),
                LocalDate.of(2026, 7, 15),
                LocalDate.of(2026, 7, 16)));

        scheduler.replenishInventory();

        verify(generationService, never()).generate(any());
    }

    @Test
    void replenishesMissingInventoryToSevenWeekdays() {
        stubActiveDates(Set.of(
                LocalDate.of(2026, 7, 14),
                LocalDate.of(2026, 7, 15)));

        scheduler.replenishInventory();

        verify(generationService).generate(LocalDate.of(2026, 7, 16));
        verify(generationService).generate(LocalDate.of(2026, 7, 17));
        verify(generationService).generate(LocalDate.of(2026, 7, 20));
        verify(generationService).generate(LocalDate.of(2026, 7, 21));
        verify(generationService).generate(LocalDate.of(2026, 7, 22));
        verify(generationService, never()).generate(LocalDate.of(2026, 7, 18));
        verify(generationService, never()).generate(LocalDate.of(2026, 7, 19));
    }

    @Test
    void continuesAfterOneDateFailsToGenerate() {
        stubActiveDates(Set.of());
        LocalDate failedDate = LocalDate.of(2026, 7, 14);
        doThrow(new IllegalStateException("generation failed"))
                .when(generationService).generate(failedDate);

        scheduler.replenishInventory();

        verify(generationService).generate(failedDate);
        verify(generationService).generate(LocalDate.of(2026, 7, 22));
    }

    private void stubActiveDates(Set<LocalDate> activeDates) {
        when(adminQuizService.hasActiveQuiz(any()))
                .thenAnswer(invocation -> activeDates.contains(invocation.getArgument(0)));
    }
}

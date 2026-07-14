package com.triread.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.triread.api.common.ApiException;
import com.triread.api.quiz.QuizMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AdminQuizServiceTest {
    @Mock AdminQuizMapper adminQuizMapper;
    @Mock QuizMapper quizMapper;
    private AdminQuizService service;

    @BeforeEach
    void setUp() {
        service = new AdminQuizService(adminQuizMapper, quizMapper,
                Clock.fixed(Instant.parse("2026-07-12T03:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createRejectsAnythingOtherThanThreePassages() {
        AdminQuizService.CreateQuiz command = new AdminQuizService.CreateQuiz(
                LocalDate.of(2026, 7, 12), List.of()
        );
        assertThatThrownBy(() -> service.createDraft(command))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    org.assertj.core.api.Assertions.assertThat(exception.getCode()).isEqualTo("INVALID_QUIZ_CONTENT");
                });
        verify(adminQuizMapper, never()).insertQuiz(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishAllowsAnotherVariantForTheSameDate() {
        long quizId = 11L;
        LocalDate date = LocalDate.of(2026, 7, 12);
        when(adminQuizMapper.findQuiz(quizId)).thenReturn(
                new AdminQuizData.QuizRow(quizId, date, "B", "DRAFT", Instant.now(), null)
        );
        when(adminQuizMapper.publish(org.mockito.ArgumentMatchers.eq(quizId), any())).thenReturn(1);

        service.publish(quizId);

        verify(adminQuizMapper).publish(org.mockito.ArgumentMatchers.eq(quizId), any());
    }

    @Test
    void deleteRejectsPublishedQuizWithoutRemovingContent() {
        long quizId = 12L;
        when(adminQuizMapper.findQuiz(quizId)).thenReturn(new AdminQuizData.QuizRow(
                quizId, LocalDate.of(2026, 7, 12), "A", "PUBLISHED", Instant.now(), Instant.now()
        ));

        assertThatThrownBy(() -> service.deleteDraft(quizId))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getCode())
                                .isEqualTo("QUIZ_DRAFT_REQUIRED"));
        verify(adminQuizMapper, never()).deleteKeys(quizId);
        verify(adminQuizMapper, never()).deleteDraft(quizId);
    }

    @Test
    void deletingReviewedQuizInvalidatesItsGenerationLog() {
        long quizId = 13L;
        when(adminQuizMapper.findQuiz(quizId)).thenReturn(new AdminQuizData.QuizRow(
                quizId, LocalDate.of(2026, 7, 13), "A", "REVIEWED", Instant.now(), null));
        when(adminQuizMapper.deleteDraft(quizId)).thenReturn(1);

        service.deleteDraft(quizId);

        verify(adminQuizMapper).invalidateGeneration(org.mockito.ArgumentMatchers.eq(quizId), any());
        verify(adminQuizMapper).deleteDraft(quizId);
    }

    @Test
    void recyclesUnusedPublishedQuizIntoNextVariantCode() {
        LocalDate targetDate = LocalDate.of(2026, 7, 20);
        when(adminQuizMapper.findActiveVariantCodesByDate(targetDate)).thenReturn(List.of("A"));
        when(adminQuizMapper.rescheduleOldestUnassignedPublishedQuiz(
                LocalDate.of(2026, 7, 12), targetDate, "B")).thenReturn(1);

        boolean recycled = service.recycleUnusedPublishedQuiz(targetDate);

        assertThat(recycled).isTrue();
        verify(adminQuizMapper).rescheduleOldestUnassignedPublishedQuiz(
                LocalDate.of(2026, 7, 12), targetDate, "B");
    }
}

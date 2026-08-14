package com.triread.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.triread.api.common.ApiException;
import com.triread.api.quiz.QuizData;
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
    void createRejectsNullCommandBeforeSaving() {
        assertThatThrownBy(() -> service.createDraft(null))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("INVALID_QUIZ_CONTENT");
                });

        verify(adminQuizMapper, never()).insertQuiz(any());
    }

    @Test
    void listsQuizDraftsWithServerSidePagination() {
        Instant createdAt = Instant.parse("2026-07-12T03:00:00Z");
        when(adminQuizMapper.countQuizzes(null, null, null)).thenReturn(14L);
        when(adminQuizMapper.countPendingQuizzes()).thenReturn(8L);
        when(adminQuizMapper.findQuizzes(null, null, null, 6, 6)).thenReturn(List.of(
                new AdminQuizData.QuizRow(7L, LocalDate.of(2026, 7, 19),
                        "B", "REVIEWED", createdAt, null)));

        AdminQuizService.QuizPage result = service.getQuizzes(1, 6, null, null, null);

        assertThat(result.page().items()).singleElement()
                .extracting(AdminQuizService.QuizSummary::quizSetId)
                .isEqualTo(7L);
        assertThat(result.page().page()).isEqualTo(1);
        assertThat(result.page().totalPages()).isEqualTo(3);
        assertThat(result.pendingCount()).isEqualTo(8);
    }

    @Test
    void reviewsDraftQuiz() {
        long quizId = 9L;
        when(adminQuizMapper.findQuiz(quizId)).thenReturn(new AdminQuizData.QuizRow(
                quizId, LocalDate.of(2026, 7, 19), "A", "DRAFT", Instant.now(), null));
        when(adminQuizMapper.markManuallyReviewed(quizId)).thenReturn(1);
        when(quizMapper.findPassages(quizId)).thenReturn(List.of());
        when(quizMapper.findQuestions(quizId)).thenReturn(List.of());
        when(quizMapper.findOptions(quizId)).thenReturn(List.of());
        when(quizMapper.findAnswerKeys(quizId)).thenReturn(List.of());

        service.review(quizId);

        verify(adminQuizMapper).markManuallyReviewed(quizId);
    }

    @Test
    void buildsQuizDetailFromMapperRows() {
        long quizId = 7L;
        long passageId = 11L;
        long questionId = 21L;
        Instant createdAt = Instant.parse("2026-07-12T03:00:00Z");
        when(adminQuizMapper.findQuiz(quizId)).thenReturn(
                new AdminQuizData.QuizRow(
                        quizId, LocalDate.of(2026, 7, 19),
                        "A", "REVIEWED", createdAt, null));
        when(quizMapper.findPassages(quizId)).thenReturn(List.of(
                new QuizData.PassageRow(
                        passageId, (short) 1, "title", "passage content", "topic")));
        when(quizMapper.findQuestions(quizId)).thenReturn(List.of(
                new QuizData.QuestionRow(
                        questionId, passageId, (short) 1, "question content")));
        when(quizMapper.findOptions(quizId)).thenReturn(List.of(
                new QuizData.OptionRow(31L, questionId, (short) 1, "first"),
                new QuizData.OptionRow(32L, questionId, (short) 2, "second")));
        when(quizMapper.findAnswerKeys(quizId)).thenReturn(List.of(
                new QuizData.AnswerKeyRow(
                        questionId, 32L, "explanation", "evidence")));

        AdminQuizService.QuizDetail detail = service.getQuiz(quizId);

        assertThat(detail.passages()).singleElement().satisfies(passage -> {
            assertThat(passage.title()).isEqualTo("title");
            assertThat(passage.questions()).singleElement().satisfies(question -> {
                assertThat(question.options()).hasSize(2);
                assertThat(question.correctOptionPosition()).isEqualTo(2);
                assertThat(question.explanation()).isEqualTo("explanation");
            });
        });
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
    void publishesSelectedQuizzesAtOnce() {
        long firstQuizId = 21L;
        long secondQuizId = 22L;
        when(adminQuizMapper.findQuiz(firstQuizId)).thenReturn(quizRow(firstQuizId, "REVIEWED"));
        when(adminQuizMapper.findQuiz(secondQuizId)).thenReturn(quizRow(secondQuizId, "REVIEWED"));
        when(adminQuizMapper.publish(eq(firstQuizId), any())).thenReturn(1);
        when(adminQuizMapper.publish(eq(secondQuizId), any())).thenReturn(1);

        AdminQuizService.BulkResult result = service.publishAll(List.of(firstQuizId, secondQuizId));

        assertThat(result.processedCount()).isEqualTo(2);
        assertThat(result.quizSetIds()).containsExactly(firstQuizId, secondQuizId);
        verify(adminQuizMapper).publish(eq(firstQuizId), any());
        verify(adminQuizMapper).publish(eq(secondQuizId), any());
    }

    @Test
    void deletesSelectedDraftsAtOnce() {
        long firstQuizId = 31L;
        long secondQuizId = 32L;
        when(adminQuizMapper.findQuiz(firstQuizId)).thenReturn(quizRow(firstQuizId, "DRAFT"));
        when(adminQuizMapper.findQuiz(secondQuizId)).thenReturn(quizRow(secondQuizId, "REVIEWED"));
        when(adminQuizMapper.deleteDraft(firstQuizId)).thenReturn(1);
        when(adminQuizMapper.deleteDraft(secondQuizId)).thenReturn(1);

        AdminQuizService.BulkResult result = service.deleteAll(List.of(firstQuizId, secondQuizId));

        assertThat(result.processedCount()).isEqualTo(2);
        assertThat(result.quizSetIds()).containsExactly(firstQuizId, secondQuizId);
        verify(adminQuizMapper).deleteDraft(firstQuizId);
        verify(adminQuizMapper).deleteDraft(secondQuizId);
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

    private AdminQuizData.QuizRow quizRow(long quizId, String status) {
        return new AdminQuizData.QuizRow(
                quizId,
                LocalDate.of(2026, 7, 20),
                "A",
                status,
                Instant.now(),
                null
        );
    }
}

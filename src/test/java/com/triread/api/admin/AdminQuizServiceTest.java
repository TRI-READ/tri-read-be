package com.triread.api.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void publishRejectsDateThatAlreadyHasPublishedQuiz() {
        long quizId = 11L;
        LocalDate date = LocalDate.of(2026, 7, 12);
        when(adminQuizMapper.findQuiz(quizId)).thenReturn(
                new AdminQuizData.QuizRow(quizId, date, "DRAFT", Instant.now(), null)
        );
        when(adminQuizMapper.countPublishedByDate(date)).thenReturn(1);

        assertThatThrownBy(() -> service.publish(quizId))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getCode())
                                .isEqualTo("QUIZ_DATE_ALREADY_PUBLISHED"));
        verify(adminQuizMapper, never()).publish(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }
}

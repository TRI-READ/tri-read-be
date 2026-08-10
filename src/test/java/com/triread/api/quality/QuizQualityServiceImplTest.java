package com.triread.api.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuizQualityServiceImplTest {
    @Mock QuizQualityMapper mapper;
    private QuizQualityService service;

    @BeforeEach
    void setUp() {
        service = new QuizQualityServiceImpl(mapper);
    }

    @Test
    void marksLowCorrectRateAndDistractorConcentrationForReview() {
        QuizQualityData.QuestionRow row = questionRow(
                10, 2, 8, 20, 6, "REVIEW_REQUIRED");
        when(mapper.countQuestions(null, null)).thenReturn(1L);
        when(mapper.findQuestions(null, null, 0, 10)).thenReturn(List.of(row));
        when(mapper.findOptions(List.of(1L))).thenReturn(List.of(
                new QuizQualityData.OptionRow(11, 1, 1, "정답", 2, 20, true),
                new QuizQualityData.OptionRow(12, 1, 2, "매력적인 오답", 6, 60, false),
                new QuizQualityData.OptionRow(13, 1, 3, "오답", 1, 10, false),
                new QuizQualityData.OptionRow(14, 1, 4, "오답", 1, 10, false)
        ));
        when(mapper.countByStatus()).thenReturn(List.of(
                new QuizQualityData.StatusCount("REVIEW_REQUIRED", 1)
        ));

        QuizQualityResponse.QualityPage result = service.getQualityPage(0, 10, null, null);

        assertThat(result.reviewRequiredCount()).isEqualTo(1);
        assertThat(result.totalQuestionCount()).isEqualTo(1);
        assertThat(result.normalCount()).isZero();
        assertThat(result.page().items()).singleElement().satisfies(question -> {
            assertThat(question.status()).isEqualTo("REVIEW_REQUIRED");
            assertThat(question.reasons()).containsExactly(
                    "정답률이 20%로 너무 낮습니다.",
                    "2번 오답에 오답 응답의 75%가 집중되었습니다."
            );
            assertThat(question.options()).hasSize(4);
        });
    }

    @Test
    void keepsSmallSamplesInDataInsufficientState() {
        QuizQualityData.QuestionRow row = questionRow(
                4, 4, 0, 100, 0, "DATA_INSUFFICIENT");
        when(mapper.countQuestions("DATA_INSUFFICIENT", "양자"))
                .thenReturn(1L);
        when(mapper.findQuestions("DATA_INSUFFICIENT", "양자", 0, 50))
                .thenReturn(List.of(row));
        when(mapper.findOptions(List.of(1L))).thenReturn(List.of());
        when(mapper.countByStatus()).thenReturn(List.of(
                new QuizQualityData.StatusCount("DATA_INSUFFICIENT", 3)
        ));

        QuizQualityResponse.QualityPage result = service.getQualityPage(
                -1, 100, "data_insufficient", "  양자  ");

        assertThat(result.page().page()).isZero();
        assertThat(result.page().size()).isEqualTo(50);
        assertThat(result.dataInsufficientCount()).isEqualTo(3);
        assertThat(result.totalQuestionCount()).isEqualTo(3);
        assertThat(result.page().items().getFirst().reasons())
                .containsExactly("아직 응답이 5건 미만입니다.");
    }

    private QuizQualityData.QuestionRow questionRow(
            long responseCount,
            long correctCount,
            long incorrectCount,
            double correctRate,
            long maxWrongOptionCount,
            String status
    ) {
        return new QuizQualityData.QuestionRow(
                1, LocalDate.of(2026, 8, 9), "A", "양자 컴퓨팅", "과학·기술",
                1, 1, "알맞은 내용은?", responseCount, correctCount, incorrectCount,
                correctRate, maxWrongOptionCount, status
        );
    }
}

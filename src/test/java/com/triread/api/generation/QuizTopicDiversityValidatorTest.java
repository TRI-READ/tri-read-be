package com.triread.api.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.triread.api.admin.AdminQuizService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuizTopicDiversityValidatorTest {
    private final QuizTopicDiversityValidator validator = new QuizTopicDiversityValidator();

    @Test
    void rejectsARecentTopicWithOnlyItsAngleChanged() {
        AdminQuizService.CreateQuiz quiz = quizWithFirstTitle(
                "Historical memory and the construction of narrative");
        List<QuizGenerationData.RecentPassageRow> recent = List.of(
                new QuizGenerationData.RecentPassageRow(
                        LocalDate.of(2026, 7, 15), 1,
                        "Historical memory and modern narrative", "Humanities"));

        QuizValidation.Result result = validator.validate(quiz, recent);

        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).singleElement().satisfies(issue ->
                assertThat(issue.code()).isEqualTo("RECENT_TOPIC_OVERLAP"));
    }

    @Test
    void allowsAnUnrelatedTopicInTheSameArea() {
        AdminQuizService.CreateQuiz quiz = quizWithFirstTitle(
                "How urban architecture changes pedestrian behavior");
        List<QuizGenerationData.RecentPassageRow> recent = List.of(
                new QuizGenerationData.RecentPassageRow(
                        LocalDate.of(2026, 7, 15), 1,
                        "Historical memory and modern narrative", "Humanities"));

        assertThat(validator.validate(quiz, recent).passed()).isTrue();
    }

    private AdminQuizService.CreateQuiz quizWithFirstTitle(String title) {
        AdminQuizService.CreateQuiz valid = RuleBasedQuizValidatorTest.validQuiz();
        List<AdminQuizService.CreatePassage> passages = new java.util.ArrayList<>(valid.passages());
        AdminQuizService.CreatePassage first = passages.getFirst();
        passages.set(0, new AdminQuizService.CreatePassage(
                title, first.topic(), first.content(), first.questions()));
        return new AdminQuizService.CreateQuiz(valid.challengeDate(), passages);
    }
}

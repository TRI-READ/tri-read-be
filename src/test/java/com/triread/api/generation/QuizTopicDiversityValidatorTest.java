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

    @Test
    void rejectsARepeatedSpecificTopicEvenWhenTheTitlesDiffer() {
        AdminQuizService.CreateQuiz quiz = quizWithFirstPassage(
                "Computing beyond binary limits", "Quantum computing and qubits");
        List<QuizGenerationData.RecentPassageRow> recent = List.of(
                new QuizGenerationData.RecentPassageRow(
                        LocalDate.of(2026, 7, 15), 1,
                        "How interference changes information processing",
                        "Quantum computing and qubit interference"));

        QuizValidation.Result result = validator.validate(quiz, recent);

        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).singleElement().satisfies(issue ->
                assertThat(issue.code()).isEqualTo("RECENT_TOPIC_OVERLAP"));
    }

    @Test
    void ignoresBroadAreaLabelsWhenSpecificSubjectsDiffer() {
        AdminQuizService.CreateQuiz quiz = quizWithFirstPassage(
                "How urban architecture changes pedestrian behavior", "Humanities");
        List<QuizGenerationData.RecentPassageRow> recent = List.of(
                new QuizGenerationData.RecentPassageRow(
                        LocalDate.of(2026, 7, 15), 1,
                        "Historical memory and modern narrative", "Humanities"));

        assertThat(validator.validate(quiz, recent).passed()).isTrue();
    }

    @Test
    void ignoresCommonKoreanBroadAreaLabels() {
        for (String broadArea : List.of(
                "\uC778\uBB38\uD559", "\uC0AC\uD68C\uACFC\uD559", "\uC778\uBB38\uD559/\uC0AC\uD68C\uACFC\uD559",
                "\uACFC\uD559\uAE30\uC220", "\uACBD\uC81C\uD559", "\uBC95\uD559", "\uACBD\uC81C\uD559/\uBC95\uD559",
                "\uBC95\uACFC \uACBD\uC81C", "\uACBD\uC81C\uC640 \uBC95", "\uACBD\uC81C\uC640 \uC81C\uB3C4",
                "\uACBD\uC81C/\uBC95/\uC735\uD569", "\uD559\uC81C\uAC04")) {
            AdminQuizService.CreateQuiz quiz = quizWithFirstPassage(
                    "\uB3C4\uC2DC \uAC74\uCD95\uC774 \uBCF4\uD589\uC790 \uD589\uB3D9\uC5D0 \uBBF8\uCE58\uB294 \uC601\uD5A5", broadArea);
            List<QuizGenerationData.RecentPassageRow> recent = List.of(
                    new QuizGenerationData.RecentPassageRow(
                            LocalDate.of(2026, 7, 15), 1,
                            "\uC5B8\uC5B4\uC758 \uC0AC\uD68C\uC801 \uAD6C\uC131\uACFC \uC758\uBBF8\uC758 \uAC00\uBCC0\uC131", broadArea));

            assertThat(validator.validate(quiz, recent).passed())
                    .as("broad area label %s", broadArea)
                    .isTrue();
        }
    }

    @Test
    void allowsDifferentSubjectsInsideTheSameDiscipline() {
        AdminQuizService.CreateQuiz quiz = quizWithFirstPassage(
                "압전 효과와 유연 소자의 에너지 수확 원리", "재료공학");
        List<QuizGenerationData.RecentPassageRow> recent = List.of(
                new QuizGenerationData.RecentPassageRow(
                        LocalDate.of(2026, 7, 15), 1,
                        "고분자 반도체와 유기 전자 소자의 발전", "재료공학"));

        assertThat(validator.validate(quiz, recent).passed()).isTrue();
    }

    private AdminQuizService.CreateQuiz quizWithFirstTitle(String title) {
        AdminQuizService.CreateQuiz valid = RuleBasedQuizValidatorTest.validQuiz();
        return quizWithFirstPassage(title, valid.passages().getFirst().topic());
    }

    private AdminQuizService.CreateQuiz quizWithFirstPassage(String title, String topic) {
        AdminQuizService.CreateQuiz valid = RuleBasedQuizValidatorTest.validQuiz();
        List<AdminQuizService.CreatePassage> passages = new java.util.ArrayList<>(valid.passages());
        AdminQuizService.CreatePassage first = passages.getFirst();
        passages.set(0, new AdminQuizService.CreatePassage(
                title, topic, first.content(), first.questions()));
        return new AdminQuizService.CreateQuiz(valid.challengeDate(), passages);
    }
}

package com.triread.api.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.triread.api.admin.AdminQuizService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleBasedQuizValidatorTest {
    private final RuleBasedQuizValidator validator = new RuleBasedQuizValidator();

    @Test
    void acceptsCompleteQuizWithEvidenceAndBalancedAnswers() {
        QuizValidation.Result result = validator.validate(validQuiz());

        assertThat(result.passed()).isTrue();
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void rejectsDuplicateOptionsAndEvidenceOutsidePassage() {
        AdminQuizService.CreateQuiz valid = validQuiz();
        AdminQuizService.CreatePassage first = valid.passages().getFirst();
        AdminQuizService.CreateQuestion broken = new AdminQuizService.CreateQuestion(
                first.questions().getFirst().content(),
                List.of("중복 선택지", "중복 선택지", "선택지 3", "선택지 4"),
                1, first.questions().getFirst().explanation(), "지문에 존재하지 않는 근거"
        );
        List<AdminQuizService.CreateQuestion> questions = new ArrayList<>(first.questions());
        questions.set(0, broken);
        List<AdminQuizService.CreatePassage> passages = new ArrayList<>(valid.passages());
        passages.set(0, new AdminQuizService.CreatePassage(
                first.title(), first.topic(), first.content(), questions));

        QuizValidation.Result result = validator.validate(
                new AdminQuizService.CreateQuiz(valid.challengeDate(), passages));

        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).extracting(QuizValidation.Issue::code)
                .contains("DUPLICATE_OPTION", "EVIDENCE_NOT_IN_PASSAGE");
    }

    static AdminQuizService.CreateQuiz validQuiz() {
        List<AdminQuizService.CreatePassage> passages = new ArrayList<>();
        int answerPosition = 1;
        for (int passage = 1; passage <= 3; passage++) {
            String evidence = "이 문장은 정답을 판단하는 핵심 근거이다.";
            String content = evidence + " " + ("고등학생용 비문학 지문의 논리적 내용을 설명한다. ").repeat(40);
            List<AdminQuizService.CreateQuestion> questions = new ArrayList<>();
            for (int question = 1; question <= 3; question++) {
                questions.add(new AdminQuizService.CreateQuestion(
                        "지문 " + passage + "의 내용으로 가장 적절한 것을 고르시오. " + question,
                        List.of("첫 번째 선택지 " + question, "두 번째 선택지 " + question,
                                "세 번째 선택지 " + question, "네 번째 선택지 " + question),
                        answerPosition, "지문의 핵심 근거에 따라 해당 선택지만 옳다고 판단할 수 있다.", evidence
                ));
                answerPosition = answerPosition % 4 + 1;
            }
            passages.add(new AdminQuizService.CreatePassage(
                    "지문 " + passage, "주제 " + passage, content, questions));
        }
        return new AdminQuizService.CreateQuiz(LocalDate.of(2026, 7, 20), passages);
    }
}

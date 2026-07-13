package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedQuizValidator implements QuizContentValidator {
    private static final int PASSAGE_MIN_LENGTH = 700;
    private static final int PASSAGE_MAX_LENGTH = 4_000;

    @Override
    public QuizValidation.Result validate(AdminQuizService.CreateQuiz quiz) {
        List<QuizValidation.Issue> issues = new ArrayList<>();
        if (quiz == null || quiz.passages() == null || quiz.passages().size() != 3) {
            issues.add(error("INVALID_PASSAGE_COUNT", null, null, "Passages must contain exactly 3 items."));
            return result(issues);
        }

        Set<String> questionTexts = new HashSet<>();
        int[] correctPositions = new int[4];
        for (int passageIndex = 0; passageIndex < quiz.passages().size(); passageIndex++) {
            int passagePosition = passageIndex + 1;
            AdminQuizService.CreatePassage passage = quiz.passages().get(passageIndex);
            validatePassage(passage, passagePosition, issues);
            if (passage.questions() == null || passage.questions().size() != 3) continue;

            for (int questionIndex = 0; questionIndex < passage.questions().size(); questionIndex++) {
                int questionPosition = questionIndex + 1;
                AdminQuizService.CreateQuestion question = passage.questions().get(questionIndex);
                validateQuestion(passage, question, passagePosition, questionPosition, issues);
                String normalizedQuestion = normalize(question.content());
                if (!normalizedQuestion.isEmpty() && !questionTexts.add(normalizedQuestion)) {
                    issues.add(error("DUPLICATE_QUESTION", passagePosition, questionPosition,
                            "The same question appears more than once."));
                }
                if (question.correctOptionPosition() >= 1 && question.correctOptionPosition() <= 4) {
                    correctPositions[question.correctOptionPosition() - 1]++;
                }
            }
        }

        long usedPositions = java.util.Arrays.stream(correctPositions).filter(count -> count > 0).count();
        int maxSamePosition = java.util.Arrays.stream(correctPositions).max().orElse(0);
        if (usedPositions < 3 || maxSamePosition > 4) {
            issues.add(warning("UNBALANCED_ANSWER_POSITIONS", null, null,
                    "Correct answer positions should use at least 3 positions and repeat no position more than 4 times."));
        }
        return result(issues);
    }

    private void validatePassage(AdminQuizService.CreatePassage passage, int position,
                                 List<QuizValidation.Issue> issues) {
        if (blank(passage.title())) issues.add(warning("MISSING_TITLE", position, null, "Passage title is missing."));
        if (blank(passage.topic())) issues.add(error("MISSING_TOPIC", position, null, "Passage topic is missing."));
        int contentLength = passage.content() == null ? 0 : passage.content().trim().length();
        if (contentLength < PASSAGE_MIN_LENGTH || contentLength > PASSAGE_MAX_LENGTH) {
            issues.add(error("INVALID_PASSAGE_LENGTH", position, null,
                    "Passage length must be between " + PASSAGE_MIN_LENGTH + " and " + PASSAGE_MAX_LENGTH + " characters."));
        }
        if (passage.questions() == null || passage.questions().size() != 3) {
            issues.add(error("INVALID_QUESTION_COUNT", position, null,
                    "Each passage must contain exactly 3 questions."));
        }
    }

    private void validateQuestion(AdminQuizService.CreatePassage passage,
                                  AdminQuizService.CreateQuestion question,
                                  int passagePosition, int questionPosition,
                                  List<QuizValidation.Issue> issues) {
        if (blank(question.content()) || question.content().trim().length() < 10) {
            issues.add(error("QUESTION_TOO_SHORT", passagePosition, questionPosition,
                    "Question content must contain at least 10 characters."));
        }
        if (question.options() == null || question.options().size() != 4) {
            issues.add(error("INVALID_OPTION_COUNT", passagePosition, questionPosition,
                    "Each question must contain exactly 4 options."));
        } else {
            Set<String> options = new HashSet<>();
            for (String option : question.options()) {
                if (blank(option)) {
                    issues.add(error("EMPTY_OPTION", passagePosition, questionPosition, "Options cannot be empty."));
                } else if (!options.add(normalize(option))) {
                    issues.add(error("DUPLICATE_OPTION", passagePosition, questionPosition,
                            "Options within a question must be unique."));
                }
            }
        }
        if (question.correctOptionPosition() < 1 || question.correctOptionPosition() > 4) {
            issues.add(error("INVALID_CORRECT_POSITION", passagePosition, questionPosition,
                    "Correct option position must be between 1 and 4."));
        }
        if (blank(question.explanation()) || question.explanation().trim().length() < 20) {
            issues.add(error("EXPLANATION_TOO_SHORT", passagePosition, questionPosition,
                    "Explanation must contain at least 20 characters."));
        }
        if (blank(question.evidence())) {
            issues.add(error("MISSING_EVIDENCE", passagePosition, questionPosition,
                    "Every generated question requires evidence from the passage."));
        } else if (passage.content() != null && !normalizeWhitespace(passage.content())
                .contains(normalizeWhitespace(question.evidence()))) {
            issues.add(error("EVIDENCE_NOT_IN_PASSAGE", passagePosition, questionPosition,
                    "Evidence must be an exact excerpt from the passage."));
        }
    }

    private QuizValidation.Result result(List<QuizValidation.Issue> issues) {
        int errors = (int) issues.stream().filter(issue -> "ERROR".equals(issue.severity())).count();
        int warnings = issues.size() - errors;
        int score = Math.max(0, 100 - errors * 15 - warnings * 5);
        return new QuizValidation.Result(errors == 0, score, issues);
    }

    private QuizValidation.Issue error(String code, Integer passage, Integer question, String message) {
        return new QuizValidation.Issue("ERROR", code, passage, question, message);
    }
    private QuizValidation.Issue warning(String code, Integer passage, Integer question, String message) {
        return new QuizValidation.Issue("WARNING", code, passage, question, message);
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String normalize(String value) {
        return value == null ? "" : normalizeWhitespace(value).toLowerCase(Locale.ROOT);
    }
    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}

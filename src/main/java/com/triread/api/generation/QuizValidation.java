package com.triread.api.generation;

import java.util.List;

public final class QuizValidation {
    private QuizValidation() {}

    public record Issue(String severity, String code, Integer passagePosition,
                        Integer questionPosition, String message) {}

    public record Result(boolean passed, int score, List<Issue> issues) {
        public Result {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }
}

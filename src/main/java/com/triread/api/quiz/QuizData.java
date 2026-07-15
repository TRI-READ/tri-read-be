package com.triread.api.quiz;

import java.time.Instant;
import java.time.LocalDate;

public final class QuizData {

    private QuizData() {
    }

    public record QuizSetRow(
            long quizSetId,
            LocalDate challengeDate,
            String variantCode,
            String difficulty,
            Long attemptId,
            Integer attemptScore,
            Integer attemptTotalQuestions,
            Long attemptPassageId,
            Instant completedAt
    ) {
    }

    public record PassageRow(
            long passageId,
            short position,
            String title,
            String content,
            String topic
    ) {
    }

    public record QuestionRow(
            long questionId,
            long passageId,
            short position,
            String content
    ) {
    }

    public record OptionRow(
            long optionId,
            long questionId,
            short position,
            String content
    ) {
    }

    public record AnswerKeyRow(
            long questionId,
            long correctOptionId,
            String explanation,
            String evidence
    ) {
    }

    public static final class QuizAttemptInsert {

        private Long id;
        private final long userId;
        private final long quizSetId;
        private final int score;
        private final Instant completedAt;

        public QuizAttemptInsert(
                long userId,
                long quizSetId,
                int score,
                Instant completedAt
        ) {
            this.userId = userId;
            this.quizSetId = quizSetId;
            this.score = score;
            this.completedAt = completedAt;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public long getUserId() {
            return userId;
        }

        public long getQuizSetId() {
            return quizSetId;
        }

        public int getScore() {
            return score;
        }

        public Instant getCompletedAt() {
            return completedAt;
        }
    }

    public record AttemptAnswerInsert(
            long attemptId,
            long questionId,
            long selectedOptionId,
            boolean correct
    ) {
    }

    public record AnswerReviewInsert(
            long userId,
            long questionId,
            long sourceAttemptId
    ) {
    }
}

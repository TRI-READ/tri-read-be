package com.triread.api.admin;

import java.time.Instant;
import java.time.LocalDate;

public final class AdminQuizData {
    private AdminQuizData() {
    }

    public record QuizRow(long quizSetId, LocalDate challengeDate, String variantCode, String status,
                          Instant createdAt, Instant publishedAt) {
    }

    public static final class QuizInsert {
        private Long id;
        private final LocalDate challengeDate;
        private final String variantCode;

        public QuizInsert(LocalDate challengeDate, String variantCode) {
            this.challengeDate = challengeDate;
            this.variantCode = variantCode;
        }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public LocalDate getChallengeDate() { return challengeDate; }
        public String getVariantCode() { return variantCode; }
    }

    public static final class PassageInsert {
        private Long id;
        private final long quizSetId;
        private final int position;
        private final String title;
        private final String content;
        private final String topic;

        public PassageInsert(long quizSetId, int position, String title, String content, String topic) {
            this.quizSetId = quizSetId; this.position = position; this.title = title;
            this.content = content; this.topic = topic;
        }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public long getQuizSetId() { return quizSetId; }
        public int getPosition() { return position; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public String getTopic() { return topic; }
    }

    public static final class QuestionInsert {
        private Long id;
        private final long passageId;
        private final int position;
        private final String content;

        public QuestionInsert(long passageId, int position, String content) {
            this.passageId = passageId; this.position = position; this.content = content;
        }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public long getPassageId() { return passageId; }
        public int getPosition() { return position; }
        public String getContent() { return content; }
    }

    public static final class OptionInsert {
        private Long id;
        private final long questionId;
        private final int position;
        private final String content;

        public OptionInsert(long questionId, int position, String content) {
            this.questionId = questionId; this.position = position; this.content = content;
        }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public long getQuestionId() { return questionId; }
        public int getPosition() { return position; }
        public String getContent() { return content; }
    }
}

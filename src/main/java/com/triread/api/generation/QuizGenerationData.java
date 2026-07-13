package com.triread.api.generation;

import java.time.Instant;
import java.time.LocalDate;

public final class QuizGenerationData {
    private QuizGenerationData() {}

    public static final class GenerationLogInsert {
        private Long id;
        private final LocalDate targetDate;
        private final String aiProvider;
        private final String aiModel;
        private final String promptVersion;
        private final String status;

        public GenerationLogInsert(LocalDate targetDate, String aiProvider, String aiModel,
                                   String promptVersion, String status) {
            this.targetDate = targetDate;
            this.aiProvider = aiProvider;
            this.aiModel = aiModel;
            this.promptVersion = promptVersion;
            this.status = status;
        }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public LocalDate getTargetDate() { return targetDate; }
        public String getAiProvider() { return aiProvider; }
        public String getAiModel() { return aiModel; }
        public String getPromptVersion() { return promptVersion; }
        public String getStatus() { return status; }
    }

    public record GenerationLogRow(long generationLogId, Long quizSetId, LocalDate targetDate,
                                   String aiProvider, String aiModel, String promptVersion,
                                   String status, int attemptCount, Integer validationScore,
                                   String errorMessage, Instant createdAt, Instant updatedAt,
                                   Instant completedAt) {}

    public record ValidationResultInsert(long generationLogId, Long quizSetId, int attemptNumber,
                                         String validationType, boolean passed,
                                         int score, String issuesJson) {}

    public record ValidationResultRow(long validationResultId, long generationLogId, Long quizSetId,
                                      int attemptNumber, String validationType, boolean passed,
                                      int score, String issuesJson, Instant createdAt) {}
}

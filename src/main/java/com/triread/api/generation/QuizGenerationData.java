package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class QuizGenerationData {
    private QuizGenerationData() {}

    public static final class GenerationLogInsert {
        private Long id;
        private final LocalDate targetDate;
        private final String aiProvider;
        private final String aiModel;
        private final String promptVersion;
        private final long generationPromptId;
        private final long validationPromptId;
        private final String status;

        public GenerationLogInsert(LocalDate targetDate, String aiProvider, String aiModel,
                                   String promptVersion, long generationPromptId,
                                   long validationPromptId, String status) {
            this.targetDate = targetDate;
            this.aiProvider = aiProvider;
            this.aiModel = aiModel;
            this.promptVersion = promptVersion;
            this.generationPromptId = generationPromptId;
            this.validationPromptId = validationPromptId;
            this.status = status;
        }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public LocalDate getTargetDate() { return targetDate; }
        public String getAiProvider() { return aiProvider; }
        public String getAiModel() { return aiModel; }
        public String getPromptVersion() { return promptVersion; }
        public long getGenerationPromptId() { return generationPromptId; }
        public long getValidationPromptId() { return validationPromptId; }
        public String getStatus() { return status; }
    }

    public record GenerationLogRow(long generationLogId, Long quizSetId, LocalDate targetDate,
                                   String aiProvider, String aiModel, String promptVersion,
                                   Long generationPromptId, Long validationPromptId,
                                   String status, int attemptCount, Integer validationScore,
                                   String errorMessage, Instant createdAt, Instant updatedAt,
                                   Instant completedAt) {}

    public record GenerationStats(long successCount, long failureCount) {}

    public record ValidationResultInsert(long generationLogId, Long quizSetId, int attemptNumber,
                                         String validationType, boolean passed,
                                         int score, String issuesJson) {}

    public record ValidationResultRow(long validationResultId, long generationLogId, Long quizSetId,
                                      int attemptNumber, String validationType, boolean passed,
                                      int score, String issuesJson, Instant createdAt) {}

    public record RecentPassageRow(LocalDate challengeDate, int position,
                                   String title, String topic, String content) {}

    public record GeneratedQuiz(LocalDate challengeDate, List<GeneratedPassage> passages) {
        public GeneratedQuiz {
            passages = passages == null ? List.of() : List.copyOf(passages);
        }

        public AdminQuizService.CreateQuiz toCreateQuiz() {
            return new AdminQuizService.CreateQuiz(challengeDate, passages.stream()
                    .map(GeneratedPassage::toCreatePassage)
                    .toList());
        }
    }

    public record GeneratedPassage(String title, String topic, String content,
                                   List<GeneratedQuestion> questions) {
        public GeneratedPassage {
            questions = questions == null ? List.of() : List.copyOf(questions);
        }

        public AdminQuizService.CreatePassage toCreatePassage() {
            return new AdminQuizService.CreatePassage(title, topic, content, questions.stream()
                    .map(GeneratedQuestion::toCreateQuestion)
                    .toList());
        }
    }

    public record GeneratedQuestion(String content, List<String> options,
                                    int correctOptionPosition, String explanation,
                                    String evidence, String questionType,
                                    List<String> optionRationales) {
        public GeneratedQuestion {
            options = options == null ? List.of() : List.copyOf(options);
            optionRationales = optionRationales == null ? List.of() : List.copyOf(optionRationales);
        }

        public AdminQuizService.CreateQuestion toCreateQuestion() {
            return new AdminQuizService.CreateQuestion(content, options, correctOptionPosition,
                    explanation, evidence);
        }
    }

    public static final class ApiCallInsert {
        private Long id;
        private final long generationLogId;
        private final String provider;
        private final String model;
        private final String purpose;

        public ApiCallInsert(long generationLogId, String provider, String model, String purpose) {
            this.generationLogId = generationLogId;
            this.provider = provider;
            this.model = model;
            this.purpose = purpose;
        }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public long getGenerationLogId() { return generationLogId; }
        public String getProvider() { return provider; }
        public String getModel() { return model; }
        public String getPurpose() { return purpose; }
    }

    public record ApiUsageStats(long totalCount, long successCount, long failureCount) {}

    public record SourceBrief(long sourceBriefId, LocalDate targetDate, String status,
                              String model, String briefingText, String errorMessage,
                              List<ContentSource> sources) {
        public SourceBrief {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }

        public boolean grounded() {
            return "READY".equals(status)
                    && java.util.stream.IntStream.rangeClosed(1, 3)
                    .allMatch(position -> sources.stream()
                            .filter(source -> source.passagePosition() == position && source.verified())
                            .map(ContentSource::sourceUrl)
                            .distinct()
                            .count() >= 2);
        }
    }

    public record SourceBriefRow(long sourceBriefId, LocalDate targetDate, String status,
                                 String model, String briefingText, String errorMessage) {}

    public record ContentSource(long contentSourceId, long sourceBriefId, int passagePosition,
                                String title, String publisher, LocalDate publishedOn,
                                String sourceUrl, String summary, Instant retrievedAt,
                                boolean verified) {}

    public record SourceDiscovery(String briefingText, List<DiscoveredSource> sources) {
        public SourceDiscovery {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }

    public record DiscoveredSource(int passagePosition, String title, String publisher,
                                   LocalDate publishedOn, String sourceUrl, String summary) {}

    public static final class SourceBriefInsert {
        private Long id;
        private final LocalDate targetDate;
        private final String status;
        private final String model;
        private final String briefingText;
        private final String errorMessage;

        public SourceBriefInsert(LocalDate targetDate, String status, String model,
                                 String briefingText, String errorMessage) {
            this.targetDate = targetDate;
            this.status = status;
            this.model = model;
            this.briefingText = briefingText;
            this.errorMessage = errorMessage;
        }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public LocalDate getTargetDate() { return targetDate; }
        public String getStatus() { return status; }
        public String getModel() { return model; }
        public String getBriefingText() { return briefingText; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static final class ContentSourceInsert {
        private Long id;
        private final long sourceBriefId;
        private final int passagePosition;
        private final String title;
        private final String publisher;
        private final LocalDate publishedOn;
        private final String sourceUrl;
        private final String summary;

        public ContentSourceInsert(long sourceBriefId, DiscoveredSource source) {
            this.sourceBriefId = sourceBriefId;
            this.passagePosition = source.passagePosition();
            this.title = source.title();
            this.publisher = source.publisher();
            this.publishedOn = source.publishedOn();
            this.sourceUrl = source.sourceUrl();
            this.summary = source.summary();
        }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public long getSourceBriefId() { return sourceBriefId; }
        public int getPassagePosition() { return passagePosition; }
        public String getTitle() { return title; }
        public String getPublisher() { return publisher; }
        public LocalDate getPublishedOn() { return publishedOn; }
        public String getSourceUrl() { return sourceUrl; }
        public String getSummary() { return summary; }
    }
}

package com.triread.api.generation;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.triread.api.admin.AdminQuizService;
import com.triread.api.common.ApiException;
import com.triread.api.common.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizGenerationServiceImpl implements QuizGenerationService {
    private final QuizGenerationMapper mapper;
    private final AdminQuizService adminQuizService;
    private final RuleBasedQuizValidator ruleValidator;
    private final QuizTopicDiversityValidator topicDiversityValidator;
    private final QuizAiGateway aiGateway;
    private final QuizGenerationProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public QuizGenerationServiceImpl(QuizGenerationMapper mapper,
                                     AdminQuizService adminQuizService,
                                     RuleBasedQuizValidator ruleValidator,
                                     QuizTopicDiversityValidator topicDiversityValidator,
                                     QuizAiGateway aiGateway,
                                     QuizGenerationProperties properties,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.mapper = mapper;
        this.adminQuizService = adminQuizService;
        this.ruleValidator = ruleValidator;
        this.topicDiversityValidator = topicDiversityValidator;
        this.aiGateway = aiGateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public synchronized GenerationResult generate(LocalDate targetDate) {
        if (targetDate == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TARGET_DATE_REQUIRED", "Target date is required.");
        }
        int variantLimit = Math.max(1, properties.getVariantsPerDate());
        if (adminQuizService.countActiveQuizSets(targetDate) >= variantLimit) {
            throw new ApiException(HttpStatus.CONFLICT, "QUIZ_DATE_INVENTORY_FULL",
                    "The quiz variant inventory is already full for this date.");
        }
        if (dailyJobCount() >= Math.max(1, properties.getMaxJobsPerDay())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "QUIZ_GENERATION_DAILY_LIMIT_REACHED",
                    "The daily quiz generation budget has been exhausted.");
        }

        QuizGenerationData.GenerationLogInsert log = new QuizGenerationData.GenerationLogInsert(
                targetDate, aiGateway.provider(), aiGateway.generationModel(), aiGateway.promptVersion(), "GENERATING");
        mapper.insertLog(log);
        long logId = log.getId();
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        List<QuizGenerationData.RecentPassageRow> excludedPassages = new ArrayList<>(
                mapper.findRecentPassages(targetDate, targetDate.minusDays(7)));
        String latestRaw = null;
        String latestError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Long persistedQuizId = null;
            try {
                updateLog(logId, null, "GENERATING", attempt, null, latestRaw, null, null);
                AdminQuizService.CreateQuiz generated = aiGateway.generate(
                        targetDate, List.copyOf(excludedPassages));
                latestRaw = serialize(generated);
                updateLog(logId, null, "VALIDATING", attempt, null, latestRaw, null, null);

                QuizValidation.Result ruleResult = ruleValidator.validate(generated);
                saveValidation(logId, null, attempt, "RULE", ruleResult);
                if (!passes(ruleResult)) {
                    latestError = summarize(ruleResult);
                    updateLog(logId, null, attempt == maxAttempts ? "FAILED" : "RETRYING",
                            attempt, ruleResult.score(), latestRaw, latestError,
                            attempt == maxAttempts ? clock.instant() : null);
                    continue;
                }

                QuizValidation.Result diversityResult = topicDiversityValidator.validate(
                        generated, excludedPassages);
                saveValidation(logId, null, attempt, "DIVERSITY", diversityResult);
                excludedPassages.addAll(toRecentPassages(generated));
                if (!passes(diversityResult)) {
                    latestError = summarize(diversityResult);
                    updateLog(logId, null, attempt == maxAttempts ? "FAILED" : "RETRYING",
                            attempt, diversityResult.score(), latestRaw, latestError,
                            attempt == maxAttempts ? clock.instant() : null);
                    continue;
                }

                QuizValidation.Result aiResult = aiGateway.validate(generated);
                saveValidation(logId, null, attempt, "AI", aiResult);
                int finalScore = Math.min(Math.min(ruleResult.score(), diversityResult.score()),
                        aiResult.score());
                if (!passes(aiResult)) {
                    latestError = summarize(aiResult);
                    updateLog(logId, null, attempt == maxAttempts ? "FAILED" : "RETRYING",
                            attempt, finalScore, latestRaw, latestError,
                            attempt == maxAttempts ? clock.instant() : null);
                    continue;
                }

                AdminQuizService.QuizDetail quiz = adminQuizService.createReviewedDraft(
                        generated, aiGateway.provider(), aiGateway.generationModel(), aiGateway.promptVersion());
                long quizSetId = quiz.quiz().quizSetId();
                persistedQuizId = quizSetId;
                boolean autoPublished = properties.isAutoPublish();
                if (autoPublished) quiz = adminQuizService.publish(quizSetId);
                String status = autoPublished ? "PUBLISHED" : "READY";
                updateLog(logId, quizSetId, status, attempt, finalScore,
                        latestRaw, null, clock.instant());
                return new GenerationResult(logId, status, attempt, finalScore, autoPublished, quiz);
            } catch (ApiException exception) {
                latestError = exception.getCode() + ": " + exception.getMessage();
                if (persistedQuizId != null) {
                    updateLog(logId, persistedQuizId, "FAILED", attempt,
                            null, latestRaw, latestError, clock.instant());
                    throw exception;
                }
                boolean configurationError = exception.getCode().endsWith("_API_KEY_MISSING");
                boolean finalAttempt = attempt == maxAttempts || configurationError;
                updateLog(logId, null, finalAttempt ? "FAILED" : "RETRYING", attempt,
                        null, latestRaw, latestError, finalAttempt ? clock.instant() : null);
                if (configurationError) throw exception;
                if (!finalAttempt && isTransient(exception)) waitBeforeRetry(attempt);
            } catch (RuntimeException exception) {
                latestError = exception.getClass().getSimpleName() + ": " + exception.getMessage();
                if (persistedQuizId != null) {
                    updateLog(logId, persistedQuizId, "FAILED", attempt,
                            null, latestRaw, latestError, clock.instant());
                    throw exception;
                }
                boolean finalAttempt = attempt == maxAttempts;
                updateLog(logId, null, finalAttempt ? "FAILED" : "RETRYING", attempt,
                        null, latestRaw, latestError, finalAttempt ? clock.instant() : null);
            }
        }

        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QUIZ_GENERATION_FAILED",
                "Quiz generation failed after " + maxAttempts + " attempts. " + latestError);
    }

    @Override
    public GenerationResult retry(long generationLogId) {
        QuizGenerationData.GenerationLogRow log = mapper.findLog(generationLogId);
        if (log == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GENERATION_LOG_NOT_FOUND",
                    "The quiz generation log was not found.");
        }
        if (!"FAILED".equals(log.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "GENERATION_RETRY_NOT_ALLOWED",
                    "Only a failed generation can be retried.");
        }
        return generate(log.targetDate());
    }

    private boolean passes(QuizValidation.Result result) {
        return result.passed() && result.score() >= properties.getPassScore()
                && result.issues().stream().noneMatch(issue -> "ERROR".equals(issue.severity()));
    }

    private long dailyJobCount() {
        LocalDate today = LocalDate.now(clock);
        Instant from = today.atStartOfDay(clock.getZone()).toInstant();
        Instant until = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        return mapper.countLogsCreatedBetween(from, until);
    }

    private List<QuizGenerationData.RecentPassageRow> toRecentPassages(
            AdminQuizService.CreateQuiz quiz
    ) {
        if (quiz == null || quiz.passages() == null) return List.of();
        List<QuizGenerationData.RecentPassageRow> passages = new ArrayList<>();
        for (int index = 0; index < quiz.passages().size(); index++) {
            AdminQuizService.CreatePassage passage = quiz.passages().get(index);
            passages.add(new QuizGenerationData.RecentPassageRow(
                    quiz.challengeDate(), index + 1, passage.title(), passage.topic()));
        }
        return passages;
    }

    private boolean isTransient(ApiException exception) {
        return "GEMINI_RATE_LIMITED".equals(exception.getCode())
                || "GEMINI_UNAVAILABLE".equals(exception.getCode());
    }

    private void waitBeforeRetry(int attempt) {
        long delay = Math.max(0, properties.getRetryDelayMs()) * attempt;
        if (delay == 0) return;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "QUIZ_GENERATION_INTERRUPTED",
                    "Quiz generation retry was interrupted.");
        }
    }

    private String summarize(QuizValidation.Result result) {
        return result.issues().isEmpty() ? "Validation score was below the pass threshold."
                : result.issues().stream().limit(3).map(issue -> issue.code() + ": " + issue.message())
                .reduce((left, right) -> left + " | " + right).orElse("Validation failed.");
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Generated quiz could not be serialized", exception);
        }
    }

    private void saveValidation(long logId, Long quizSetId, int attempt, String type,
                                QuizValidation.Result result) {
        mapper.insertValidationResult(new QuizGenerationData.ValidationResultInsert(
                logId, quizSetId, attempt, type, result.passed(), result.score(), serialize(result.issues())));
    }

    private void updateLog(long logId, Long quizSetId, String status, int attempt,
                           Integer score, String raw, String error, Instant completedAt) {
        mapper.updateLog(logId, quizSetId, status, attempt, score, raw, error, completedAt, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public GenerationLogPage getLogs(int requestedPage, int requestedSize) {
        int page = PageResponse.page(requestedPage);
        int size = PageResponse.size(requestedSize);
        long total = mapper.countLogs();
        List<QuizGenerationData.GenerationLogRow> logs = mapper.findLogs(page * size, size);
        QuizGenerationData.GenerationStats stats = mapper.getStats();
        return new GenerationLogPage(PageResponse.of(logs, page, size, total),
                stats.successCount(), stats.failureCount());
    }

    @Override
    @Transactional(readOnly = true)
    public GenerationDetail getLog(long generationLogId) {
        QuizGenerationData.GenerationLogRow log = mapper.findLog(generationLogId);
        if (log == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GENERATION_LOG_NOT_FOUND",
                    "The quiz generation log was not found.");
        }
        List<ValidationResult> validations = mapper.findValidationResults(generationLogId).stream()
                .map(row -> new ValidationResult(row.validationResultId(), row.attemptNumber(),
                        row.validationType(), row.passed(), row.score(), deserializeIssues(row.issuesJson()),
                        row.createdAt()))
                .toList();
        return new GenerationDetail(log, validations);
    }

    private List<QuizValidation.Issue> deserializeIssues(String issuesJson) {
        try {
            return objectMapper.readValue(issuesJson, new TypeReference<>() {});
        } catch (JacksonException exception) {
            throw new IllegalStateException("Validation issues could not be deserialized", exception);
        }
    }
}

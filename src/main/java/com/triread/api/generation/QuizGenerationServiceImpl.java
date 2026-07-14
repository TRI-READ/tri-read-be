package com.triread.api.generation;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.triread.api.admin.AdminQuizService;
import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizGenerationServiceImpl implements QuizGenerationService {
    private final QuizGenerationMapper mapper;
    private final AdminQuizService adminQuizService;
    private final RuleBasedQuizValidator ruleValidator;
    private final QuizAiGateway aiGateway;
    private final QuizGenerationProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public QuizGenerationServiceImpl(QuizGenerationMapper mapper,
                                     AdminQuizService adminQuizService,
                                     RuleBasedQuizValidator ruleValidator,
                                     QuizAiGateway aiGateway,
                                     QuizGenerationProperties properties,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.mapper = mapper;
        this.adminQuizService = adminQuizService;
        this.ruleValidator = ruleValidator;
        this.aiGateway = aiGateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public GenerationResult generate(LocalDate targetDate) {
        if (targetDate == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TARGET_DATE_REQUIRED", "Target date is required.");
        }
        int variantLimit = Math.max(1, properties.getVariantsPerDate());
        if (adminQuizService.countActiveQuizSets(targetDate) >= variantLimit) {
            throw new ApiException(HttpStatus.CONFLICT, "QUIZ_DATE_INVENTORY_FULL",
                    "The quiz variant inventory is already full for this date.");
        }

        QuizGenerationData.GenerationLogInsert log = new QuizGenerationData.GenerationLogInsert(
                targetDate, aiGateway.provider(), aiGateway.generationModel(), aiGateway.promptVersion(), "GENERATING");
        mapper.insertLog(log);
        long logId = log.getId();
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        String latestRaw = null;
        String latestError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Long persistedQuizId = null;
            try {
                updateLog(logId, null, "GENERATING", attempt, null, latestRaw, null, null);
                AdminQuizService.CreateQuiz generated = aiGateway.generate(targetDate);
                latestRaw = serialize(generated);
                updateLog(logId, null, "VALIDATING", attempt, null, latestRaw, null, null);

                QuizValidation.Result ruleResult = ruleValidator.validate(generated);
                if (!passes(ruleResult)) {
                    saveValidation(logId, null, attempt, "RULE", ruleResult);
                    latestError = summarize(ruleResult);
                    updateLog(logId, null, attempt == maxAttempts ? "FAILED" : "RETRYING",
                            attempt, ruleResult.score(), latestRaw, latestError,
                            attempt == maxAttempts ? clock.instant() : null);
                    continue;
                }

                QuizValidation.Result aiResult = aiGateway.validate(generated);
                int finalScore = Math.min(ruleResult.score(), aiResult.score());
                if (!passes(aiResult)) {
                    saveValidation(logId, null, attempt, "RULE", ruleResult);
                    saveValidation(logId, null, attempt, "AI", aiResult);
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
                saveValidation(logId, quizSetId, attempt, "RULE", ruleResult);
                saveValidation(logId, quizSetId, attempt, "AI", aiResult);

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

    private boolean passes(QuizValidation.Result result) {
        return result.passed() && result.score() >= properties.getPassScore()
                && result.issues().stream().noneMatch(issue -> "ERROR".equals(issue.severity()));
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
    public List<QuizGenerationData.GenerationLogRow> getLogs() {
        return mapper.findLogs();
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

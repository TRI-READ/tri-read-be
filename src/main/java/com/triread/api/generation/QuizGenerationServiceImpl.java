package com.triread.api.generation;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.triread.api.admin.AdminQuizService;
import com.triread.api.common.ApiException;
import com.triread.api.common.PageResponse;
import com.triread.api.prompt.PromptTemplateService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
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
    private final PromptTemplateService promptTemplateService;
    private final QuizGenerationProperties properties;
    private final AiApiUsageService apiUsageService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public QuizGenerationServiceImpl(QuizGenerationMapper mapper,
                                     AdminQuizService adminQuizService,
                                     RuleBasedQuizValidator ruleValidator,
                                     QuizTopicDiversityValidator topicDiversityValidator,
                                     QuizAiGateway aiGateway,
                                     PromptTemplateService promptTemplateService,
                                     QuizGenerationProperties properties,
                                     AiApiUsageService apiUsageService,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.mapper = mapper;
        this.adminQuizService = adminQuizService;
        this.ruleValidator = ruleValidator;
        this.topicDiversityValidator = topicDiversityValidator;
        this.aiGateway = aiGateway;
        this.promptTemplateService = promptTemplateService;
        this.properties = properties;
        this.apiUsageService = apiUsageService;
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

        PromptTemplateService.ActivePrompts prompts = promptTemplateService.getActivePrompts();
        QuizGenerationData.GenerationLogInsert log = new QuizGenerationData.GenerationLogInsert(
                targetDate, aiGateway.provider(), aiGateway.generationModel(), prompts.versionLabel(),
                prompts.generation().promptTemplateId(), prompts.validation().promptTemplateId(), "GENERATING");
        mapper.insertLog(log);
        long logId = log.getId();
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        List<QuizGenerationData.RecentPassageRow> excludedPassages = new ArrayList<>(
                mapper.findRecentPassages(targetDate, targetDate.minusDays(7)));
        String latestRaw = null;
        String latestError = null;
        QuizGenerationData.GeneratedQuiz candidate = null;
        List<QuizValidation.Issue> repairIssues = List.of();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Long persistedQuizId = null;
            try {
                updateLog(logId, null, "GENERATING", attempt, null, latestRaw, null, null);
                if (candidate == null) {
                    candidate = callAi(logId, "GENERATION", aiGateway.generationModel(),
                            () -> aiGateway.generate(targetDate, List.copyOf(excludedPassages),
                                    prompts.generation()));
                } else {
                    QuizGenerationData.GeneratedQuiz previousCandidate = candidate;
                    List<QuizValidation.Issue> previousIssues = repairIssues;
                    candidate = callAi(logId, "REPAIR", aiGateway.generationModel(),
                            () -> aiGateway.repair(previousCandidate, previousIssues,
                                    prompts.generation()));
                }
                latestRaw = serialize(candidate);
                AdminQuizService.CreateQuiz generated = candidate.toCreateQuiz();
                updateLog(logId, null, "VALIDATING", attempt, null, latestRaw, null, null);

                QuizValidation.Result ruleResult = ruleValidator.validate(candidate);
                saveValidation(logId, null, attempt, "RULE", ruleResult);
                if (!passes(ruleResult)) {
                    repairIssues = ruleResult.issues();
                    latestError = summarize(ruleResult);
                    updateLog(logId, null, attempt == maxAttempts ? "FAILED" : "RETRYING",
                            attempt, ruleResult.score(), latestRaw, latestError,
                            attempt == maxAttempts ? clock.instant() : null);
                    continue;
                }

                QuizValidation.Result diversityResult = topicDiversityValidator.validate(
                        generated, excludedPassages);
                saveValidation(logId, null, attempt, "DIVERSITY", diversityResult);
                if (!passes(diversityResult)) {
                    repairIssues = diversityResult.issues();
                    latestError = summarize(diversityResult);
                    updateLog(logId, null, attempt == maxAttempts ? "FAILED" : "RETRYING",
                            attempt, diversityResult.score(), latestRaw, latestError,
                            attempt == maxAttempts ? clock.instant() : null);
                    continue;
                }

                int finalScore = Math.min(ruleResult.score(), diversityResult.score());
                if (properties.isAiValidationEnabled()) {
                    QuizGenerationData.GeneratedQuiz validatedCandidate = candidate;
                    QuizValidation.Result aiResult = callAi(logId, "VALIDATION",
                            properties.getGemini().getValidationModel(),
                            () -> aiGateway.validate(validatedCandidate, prompts.validation()));
                    saveValidation(logId, null, attempt, "AI", aiResult);
                    finalScore = Math.min(finalScore, aiResult.score());
                    if (!passes(aiResult)) {
                        repairIssues = aiResult.issues();
                        latestError = summarize(aiResult);
                        updateLog(logId, null, attempt == maxAttempts ? "FAILED" : "RETRYING",
                                attempt, finalScore, latestRaw, latestError,
                                attempt == maxAttempts ? clock.instant() : null);
                        continue;
                    }
                }

                AdminQuizService.QuizDetail quiz = adminQuizService.createReviewedDraft(
                        generated, aiGateway.provider(), aiGateway.generationModel(), prompts.versionLabel(),
                        prompts.generation().promptTemplateId(), prompts.validation().promptTemplateId());
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
                boolean terminalError = exception.getCode().endsWith("_API_KEY_MISSING")
                        || "QUIZ_GENERATION_API_DAILY_LIMIT_REACHED".equals(exception.getCode());
                boolean finalAttempt = attempt == maxAttempts || terminalError;
                updateLog(logId, null, finalAttempt ? "FAILED" : "RETRYING", attempt,
                        null, latestRaw, latestError, finalAttempt ? clock.instant() : null);
                if (terminalError) throw exception;
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

    private <T> T callAi(long logId, String purpose, String model, Supplier<T> call) {
        long apiCallId = apiUsageService.start(logId, aiGateway.provider(), model, purpose);
        try {
            T result = call.get();
            apiUsageService.success(apiCallId);
            return result;
        } catch (ApiException exception) {
            apiUsageService.failure(apiCallId, exception.getCode());
            throw exception;
        } catch (RuntimeException exception) {
            apiUsageService.failure(apiCallId, exception.getClass().getSimpleName());
            throw exception;
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
    public GenerationLogPage getLogs(int requestedPage, int requestedSize,
                                     String requestedStatus, LocalDate targetDate) {
        int page = PageResponse.page(requestedPage);
        int size = PageResponse.size(requestedSize);
        String status = normalizeStatus(requestedStatus);
        long total = mapper.countLogs(status, targetDate);
        List<QuizGenerationData.GenerationLogRow> logs = mapper.findLogs(status, targetDate,
                page * size, size);
        QuizGenerationData.GenerationStats stats = mapper.getStats();
        return new GenerationLogPage(PageResponse.of(logs, page, size, total),
                stats.successCount(), stats.failureCount(), apiUsageService.todayUsage(),
                properties.isAiValidationEnabled());
    }

    private String normalizeStatus(String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) return null;
        String status = requestedStatus.trim().toUpperCase();
        if (!Set.of("GENERATING", "VALIDATING", "RETRYING", "READY", "PUBLISHED", "FAILED")
                .contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GENERATION_STATUS_INVALID",
                    "The generation status filter is invalid.");
        }
        return status;
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

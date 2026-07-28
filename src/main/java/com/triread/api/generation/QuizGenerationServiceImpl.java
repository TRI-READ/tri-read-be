package com.triread.api.generation;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.triread.api.admin.AdminQuizService;
import com.triread.api.common.ApiException;
import com.triread.api.common.PageResponse;
import com.triread.api.notification.DiscordNotificationService;
import com.triread.api.prompt.PromptTemplateService;
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
    private final PromptTemplateService promptTemplateService;
    private final QuizGenerationProperties properties;
    private final AiApiUsageService apiUsageService;
    private final ObjectMapper objectMapper;
    private final DiscordNotificationService notificationService;
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
                                     DiscordNotificationService notificationService,
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
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Override
    public synchronized GenerationResult generate(LocalDate targetDate) {
        try {
            return generateInternal(targetDate);
        } catch (RuntimeException exception) {
            if (shouldNotifyFailure(exception)) {
                notificationService.notifyFailure("QUIZ_GENERATION",
                        "퀴즈 생성 최종 실패",
                        "대상 날짜: " + targetDate + "\n오류: " + errorSummary(exception));
            }
            throw exception;
        }
    }

    private GenerationResult generateInternal(LocalDate targetDate) {
        validateGenerationRequest(targetDate);

        PromptTemplateService.ActivePrompts prompts = promptTemplateService.getActivePrompts();
        long logId = createGenerationLog(targetDate, prompts);
        List<QuizGenerationData.RecentPassageRow> recentPassages =
                loadRecentPassages(targetDate);
        QuizGenerationData.SourceBrief sourceBrief =
                loadSourceBrief(logId, targetDate, recentPassages);

        return runGenerationAttempts(
                logId, targetDate, recentPassages, sourceBrief, prompts);
    }

    private void validateGenerationRequest(LocalDate targetDate) {
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
    }

    private long createGenerationLog(
            LocalDate targetDate,
            PromptTemplateService.ActivePrompts prompts
    ) {
        QuizGenerationData.GenerationLogInsert log = new QuizGenerationData.GenerationLogInsert(
                targetDate, aiGateway.provider(), aiGateway.generationModel(), prompts.versionLabel(),
                prompts.generation().promptTemplateId(), prompts.validation().promptTemplateId(), "GENERATING");
        mapper.insertLog(log);
        return log.getId();
    }

    private List<QuizGenerationData.RecentPassageRow> loadRecentPassages(
            LocalDate targetDate
    ) {
        return new ArrayList<>(
                mapper.findRecentPassages(targetDate, targetDate.minusDays(7)));
    }

    private QuizGenerationData.SourceBrief loadSourceBrief(
            long logId,
            LocalDate targetDate,
            List<QuizGenerationData.RecentPassageRow> recentPassages
    ) {
        try {
            return resolveSourceBrief(logId, targetDate, recentPassages);
        } catch (RuntimeException exception) {
            String error = errorSummary(exception);
            updateLog(logId, null, "FAILED", 0, null, null, error, clock.instant());
            throw exception;
        }
    }

    private GenerationResult runGenerationAttempts(
            long logId,
            LocalDate targetDate,
            List<QuizGenerationData.RecentPassageRow> recentPassages,
            QuizGenerationData.SourceBrief sourceBrief,
            PromptTemplateService.ActivePrompts prompts
    ) {
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        String latestRaw = null;
        String latestError = null;
        QuizGenerationData.GeneratedQuiz candidate = null;
        List<QuizValidation.Issue> repairIssues = List.of();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Long persistedQuizId = null;
            try {
                updateLog(logId, null, "GENERATING", attempt, null, latestRaw, null, null);
                candidate = createCandidate(
                        logId, targetDate, recentPassages, sourceBrief,
                        prompts.generation(), candidate, repairIssues);
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
                        generated, recentPassages);
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
                    QuizValidation.Result aiResult =
                            requestAiValidation(logId, candidate, prompts.validation());
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
                if (sourceBrief.grounded()) {
                    mapper.linkSourcesToQuiz(quizSetId, sourceBrief.sourceBriefId());
                }
                boolean autoPublished = properties.isAutoPublish() && sourceBrief.grounded();
                if (autoPublished) {
                    quiz = adminQuizService.publish(quizSetId);
                }
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
                if (terminalError) {
                    throw exception;
                }
                if (!finalAttempt && isTransient(exception)) {
                    waitBeforeRetry(attempt);
                }
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

    private QuizGenerationData.GeneratedQuiz createCandidate(
            long logId,
            LocalDate targetDate,
            List<QuizGenerationData.RecentPassageRow> recentPassages,
            QuizGenerationData.SourceBrief sourceBrief,
            PromptTemplateService.PromptSnapshot prompt,
            QuizGenerationData.GeneratedQuiz previousCandidate,
            List<QuizValidation.Issue> repairIssues
    ) {
        if (previousCandidate == null) {
            return requestQuizGeneration(
                    logId, targetDate, recentPassages, sourceBrief, prompt);
        }

        return requestQuizRepair(
                logId, previousCandidate, repairIssues, sourceBrief, prompt);
    }

    private boolean shouldNotifyFailure(RuntimeException exception) {
        if (!(exception instanceof ApiException)) {
            return true;
        }

        ApiException apiException = (ApiException) exception;
        String code = apiException.getCode();
        if ("TARGET_DATE_REQUIRED".equals(code)) {
            return false;
        }
        if ("QUIZ_DATE_INVENTORY_FULL".equals(code)) {
            return false;
        }
        if ("QUIZ_GENERATION_DAILY_LIMIT_REACHED".equals(code)) {
            return false;
        }
        return !"QUIZ_GENERATION_API_DAILY_LIMIT_REACHED".equals(code);
    }

    private String errorSummary(RuntimeException exception) {
        if (exception instanceof ApiException) {
            ApiException apiException = (ApiException) exception;
            return apiException.getCode() + ": " + apiException.getMessage();
        }
        return exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }

    private QuizGenerationData.SourceBrief resolveSourceBrief(
            long logId,
            LocalDate targetDate,
            List<QuizGenerationData.RecentPassageRow> recentPassages
    ) {
        QuizGenerationData.SourceBriefRow existing = mapper.findSourceBrief(targetDate);
        if (existing != null && "READY".equals(existing.status())) {
            return toSourceBrief(existing);
        }
        if (!properties.isSourceGroundingEnabled()) {
            return new QuizGenerationData.SourceBrief(0, targetDate, "DISABLED",
                    aiGateway.sourceModel(), "", null, List.of());
        }
        try {
            QuizGenerationData.SourceDiscovery discovery =
                    requestSourceDiscovery(logId, targetDate, recentPassages);
            QuizGenerationData.SourceBriefInsert insert = new QuizGenerationData.SourceBriefInsert(
                    targetDate, "READY", aiGateway.sourceModel(), discovery.briefingText(), null);
            mapper.insertSourceBrief(insert);
            for (QuizGenerationData.DiscoveredSource source : discovery.sources()) {
                QuizGenerationData.ContentSourceInsert contentSource =
                        new QuizGenerationData.ContentSourceInsert(insert.getId(), source);
                mapper.insertContentSource(contentSource);
            }
            QuizGenerationData.SourceBriefRow saved = mapper.findSourceBrief(targetDate);
            return toSourceBrief(saved);
        } catch (RuntimeException exception) {
            QuizGenerationData.SourceBriefInsert failed = new QuizGenerationData.SourceBriefInsert(
                    targetDate, "FAILED", aiGateway.sourceModel(), null,
                    exception.getMessage());
            mapper.insertSourceBrief(failed);
            throw exception;
        }
    }

    private QuizGenerationData.SourceBrief toSourceBrief(
            QuizGenerationData.SourceBriefRow row) {
        return new QuizGenerationData.SourceBrief(
                row.sourceBriefId(), row.targetDate(), row.status(), row.model(),
                row.briefingText(), row.errorMessage(),
                mapper.findSourcesByBrief(row.sourceBriefId()));
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
        if (!result.passed()) {
            return false;
        }
        if (result.score() < properties.getPassScore()) {
            return false;
        }
        for (QuizValidation.Issue issue : result.issues()) {
            if ("ERROR".equals(issue.severity())) {
                return false;
            }
        }
        return true;
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
        if (delay == 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "QUIZ_GENERATION_INTERRUPTED",
                    "Quiz generation retry was interrupted.");
        }
    }

    private String summarize(QuizValidation.Result result) {
        if (result.issues().isEmpty()) {
            return "Validation score was below the pass threshold.";
        }

        StringBuilder summary = new StringBuilder();
        int count = Math.min(3, result.issues().size());
        for (int index = 0; index < count; index++) {
            QuizValidation.Issue issue = result.issues().get(index);
            if (index > 0) {
                summary.append(" | ");
            }
            summary.append(issue.code());
            summary.append(": ");
            summary.append(issue.message());
        }
        return summary.toString();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Generated quiz could not be serialized", exception);
        }
    }

    private QuizGenerationData.GeneratedQuiz requestQuizGeneration(
            long logId,
            LocalDate targetDate,
            List<QuizGenerationData.RecentPassageRow> recentPassages,
            QuizGenerationData.SourceBrief sourceBrief,
            PromptTemplateService.PromptSnapshot prompt
    ) {
        long apiCallId = apiUsageService.start(
                logId, aiGateway.provider(), aiGateway.generationModel(), "GENERATION");
        try {
            QuizGenerationData.GeneratedQuiz result;
            List<QuizGenerationData.RecentPassageRow> passages =
                    new ArrayList<>(recentPassages);
            if (sourceBrief.grounded()) {
                result = aiGateway.generate(sourceBrief, passages, prompt);
            } else {
                result = aiGateway.generate(targetDate, passages, prompt);
            }
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

    private QuizGenerationData.GeneratedQuiz requestQuizRepair(
            long logId,
            QuizGenerationData.GeneratedQuiz previousCandidate,
            List<QuizValidation.Issue> repairIssues,
            QuizGenerationData.SourceBrief sourceBrief,
            PromptTemplateService.PromptSnapshot prompt
    ) {
        long apiCallId = apiUsageService.start(
                logId, aiGateway.provider(), aiGateway.generationModel(), "REPAIR");
        try {
            QuizGenerationData.GeneratedQuiz result;
            if (sourceBrief.grounded()) {
                result = aiGateway.repair(
                        previousCandidate, repairIssues, prompt, sourceBrief);
            } else {
                result = aiGateway.repair(previousCandidate, repairIssues, prompt);
            }
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

    private QuizValidation.Result requestAiValidation(
            long logId,
            QuizGenerationData.GeneratedQuiz candidate,
            PromptTemplateService.PromptSnapshot prompt
    ) {
        String model = properties.getGemini().getValidationModel();
        long apiCallId = apiUsageService.start(
                logId, aiGateway.provider(), model, "VALIDATION");
        try {
            QuizValidation.Result result = aiGateway.validate(candidate, prompt);
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

    private QuizGenerationData.SourceDiscovery requestSourceDiscovery(
            long logId,
            LocalDate targetDate,
            List<QuizGenerationData.RecentPassageRow> recentPassages
    ) {
        long apiCallId = apiUsageService.start(
                logId, aiGateway.provider(), aiGateway.sourceModel(), "SOURCE_DISCOVERY");
        try {
            QuizGenerationData.SourceDiscovery result =
                    aiGateway.discoverSources(targetDate, recentPassages);
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
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return null;
        }
        String status = requestedStatus.trim().toUpperCase();
        boolean validStatus = "GENERATING".equals(status)
                || "VALIDATING".equals(status)
                || "RETRYING".equals(status)
                || "READY".equals(status)
                || "PUBLISHED".equals(status)
                || "FAILED".equals(status);
        if (!validStatus) {
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
        List<ValidationResult> validations = new ArrayList<>();
        List<QuizGenerationData.ValidationResultRow> rows =
                mapper.findValidationResults(generationLogId);
        for (QuizGenerationData.ValidationResultRow row : rows) {
            ValidationResult validation = new ValidationResult(
                    row.validationResultId(),
                    row.attemptNumber(),
                    row.validationType(),
                    row.passed(),
                    row.score(),
                    deserializeIssues(row.issuesJson()),
                    row.createdAt()
            );
            validations.add(validation);
        }
        return new GenerationDetail(log, validations,
                mapper.findSourcesByGenerationLog(generationLogId));
    }

    private List<QuizValidation.Issue> deserializeIssues(String issuesJson) {
        try {
            TypeReference<List<QuizValidation.Issue>> type =
                    new TypeReference<List<QuizValidation.Issue>>() {};
            return objectMapper.readValue(issuesJson, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Validation issues could not be deserialized", exception);
        }
    }
}

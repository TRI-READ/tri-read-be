package com.triread.api.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.triread.api.admin.AdminQuizService;
import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class QuizGenerationServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");

    @Mock QuizGenerationMapper mapper;
    @Mock AdminQuizService adminQuizService;
    @Mock RuleBasedQuizValidator ruleValidator;
    @Mock QuizAiGateway aiGateway;
    private QuizGenerationProperties properties;
    private QuizGenerationServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new QuizGenerationProperties();
        properties.setMaxAttempts(3);
        properties.setPassScore(90);
        properties.setRetryDelayMs(0);
        service = new QuizGenerationServiceImpl(mapper, adminQuizService, ruleValidator,
                new QuizTopicDiversityValidator(), aiGateway, properties,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(aiGateway.generationModel()).thenReturn("generation-model");
        lenient().when(aiGateway.provider()).thenReturn("GEMINI");
        lenient().when(aiGateway.promptVersion()).thenReturn("v1");
        lenient().doAnswer(invocation -> {
            QuizGenerationData.GenerationLogInsert log = invocation.getArgument(0);
            log.setId(42L);
            return null;
        }).when(mapper).insertLog(any());
    }

    @Test
    void savesReviewedQuizAfterBothValidatorsPass() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        AdminQuizService.CreateQuiz generated = RuleBasedQuizValidatorTest.validQuiz();
        QuizValidation.Result ruleResult = new QuizValidation.Result(true, 100, List.of());
        QuizValidation.Result aiResult = new QuizValidation.Result(true, 96, List.of());
        AdminQuizService.QuizDetail detail = detail(7L, date, "REVIEWED");

        when(aiGateway.generate(eq(date), anyList())).thenReturn(generated);
        when(ruleValidator.validate(generated)).thenReturn(ruleResult);
        when(aiGateway.validate(generated)).thenReturn(aiResult);
        when(adminQuizService.createReviewedDraft(generated, "GEMINI", "generation-model", "v1"))
                .thenReturn(detail);

        QuizGenerationService.GenerationResult result = service.generate(date);

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.validationScore()).isEqualTo(96);
        assertThat(result.autoPublished()).isFalse();
        verify(mapper, times(3)).insertValidationResult(any());
        verify(mapper).updateLog(eq(42L), eq(7L), eq("READY"), eq(1), eq(96),
                anyString(), isNull(), eq(NOW), eq(NOW));
    }

    @Test
    void retriesGenerationWhenRuleValidationFails() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        AdminQuizService.CreateQuiz generated = RuleBasedQuizValidatorTest.validQuiz();
        QuizValidation.Result failed = new QuizValidation.Result(false, 70, List.of(
                new QuizValidation.Issue("ERROR", "AMBIGUOUS", 1, 1, "Ambiguous question")));
        QuizValidation.Result passed = new QuizValidation.Result(true, 100, List.of());

        when(aiGateway.generate(eq(date), anyList())).thenReturn(generated);
        when(ruleValidator.validate(generated)).thenReturn(failed, passed);
        when(aiGateway.validate(generated)).thenReturn(passed);
        when(adminQuizService.createReviewedDraft(any(), anyString(), anyString(), anyString()))
                .thenReturn(detail(8L, date, "REVIEWED"));

        QuizGenerationService.GenerationResult result = service.generate(date);

        assertThat(result.attemptCount()).isEqualTo(2);
        verify(aiGateway, times(2)).generate(eq(date), anyList());
        verify(mapper).updateLog(eq(42L), isNull(), eq("RETRYING"), eq(1), eq(70),
                anyString(), anyString(), isNull(), eq(NOW));
    }

    @Test
    void retriesTransientGeminiFailure() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        AdminQuizService.CreateQuiz generated = RuleBasedQuizValidatorTest.validQuiz();
        QuizValidation.Result passed = new QuizValidation.Result(true, 100, List.of());

        when(aiGateway.generate(eq(date), anyList()))
                .thenThrow(new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "GEMINI_UNAVAILABLE", "Temporarily unavailable"))
                .thenReturn(generated);
        when(ruleValidator.validate(generated)).thenReturn(passed);
        when(aiGateway.validate(generated)).thenReturn(passed);
        when(adminQuizService.createReviewedDraft(any(), anyString(), anyString(), anyString()))
                .thenReturn(detail(9L, date, "REVIEWED"));

        QuizGenerationService.GenerationResult result = service.generate(date);

        assertThat(result.attemptCount()).isEqualTo(2);
        verify(aiGateway, times(2)).generate(eq(date), anyList());
    }

    @Test
    void rejectsGenerationWhenDateAlreadyHasConfiguredVariantCount() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        properties.setVariantsPerDate(3);
        when(adminQuizService.countActiveQuizSets(date)).thenReturn(3);

        assertThatThrownBy(() -> service.generate(date))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("QUIZ_DATE_INVENTORY_FULL"));
    }

    @Test
    void returnsGenerationLogWithStructuredValidationIssues() {
        QuizGenerationData.GenerationLogRow log = new QuizGenerationData.GenerationLogRow(
                42L, 7L, LocalDate.of(2026, 7, 20), "GEMINI", "generation-model", "v1",
                "READY", 1, 95, null, NOW, NOW, NOW);
        when(mapper.findLog(42L)).thenReturn(log);
        when(mapper.findValidationResults(42L)).thenReturn(List.of(
                new QuizGenerationData.ValidationResultRow(9L, 42L, 7L, 1, "AI", true, 95,
                        "[{\"severity\":\"WARNING\",\"code\":\"WORDING\","
                                + "\"passagePosition\":1,\"questionPosition\":2,"
                                + "\"message\":\"Review wording\"}]", NOW)));

        QuizGenerationService.GenerationDetail detail = service.getLog(42L);

        assertThat(detail.log()).isEqualTo(log);
        assertThat(detail.validations()).singleElement().satisfies(validation -> {
            assertThat(validation.validationType()).isEqualTo("AI");
            assertThat(validation.issues()).singleElement().satisfies(issue ->
                    assertThat(issue.code()).isEqualTo("WORDING"));
        });
    }

    @Test
    void listsGenerationLogsWithGlobalStatsAndPagination() {
        QuizGenerationData.GenerationLogRow log = new QuizGenerationData.GenerationLogRow(
                42L, 7L, LocalDate.of(2026, 7, 20), "GEMINI", "generation-model", "v1",
                "READY", 1, 95, null, NOW, NOW, NOW);
        when(mapper.countLogs()).thenReturn(27L);
        when(mapper.findLogs(10, 10)).thenReturn(List.of(log));
        when(mapper.getStats()).thenReturn(new QuizGenerationData.GenerationStats(18L, 9L));

        QuizGenerationService.GenerationLogPage result = service.getLogs(1, 10);

        assertThat(result.page().items()).containsExactly(log);
        assertThat(result.page().page()).isEqualTo(1);
        assertThat(result.page().totalPages()).isEqualTo(3);
        assertThat(result.successCount()).isEqualTo(18);
        assertThat(result.failureCount()).isEqualTo(9);
    }

    @Test
    void rejectsUnknownGenerationLog() {
        assertThatThrownBy(() -> service.getLog(999L))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("GENERATION_LOG_NOT_FOUND"));
    }

    @Test
    void retriesFailedGenerationUsingOriginalTargetDate() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        QuizGenerationData.GenerationLogRow failedLog = new QuizGenerationData.GenerationLogRow(
                41L, null, date, "GEMINI", "generation-model", "v1",
                "FAILED", 3, null, "temporary failure", NOW, NOW, NOW);
        AdminQuizService.CreateQuiz generated = RuleBasedQuizValidatorTest.validQuiz();
        QuizValidation.Result passed = new QuizValidation.Result(true, 100, List.of());
        when(mapper.findLog(41L)).thenReturn(failedLog);
        when(aiGateway.generate(eq(date), anyList())).thenReturn(generated);
        when(ruleValidator.validate(generated)).thenReturn(passed);
        when(aiGateway.validate(generated)).thenReturn(passed);
        when(adminQuizService.createReviewedDraft(any(), anyString(), anyString(), anyString()))
                .thenReturn(detail(10L, date, "REVIEWED"));

        QuizGenerationService.GenerationResult result = service.retry(41L);

        assertThat(result.quiz().quiz().challengeDate()).isEqualTo(date);
        verify(mapper).insertLog(any());
    }

    @Test
    void rejectsRetryForSuccessfulGeneration() {
        QuizGenerationData.GenerationLogRow readyLog = new QuizGenerationData.GenerationLogRow(
                41L, 10L, LocalDate.of(2026, 7, 20), "GEMINI", "generation-model", "v1",
                "READY", 1, 100, null, NOW, NOW, NOW);
        when(mapper.findLog(41L)).thenReturn(readyLog);

        assertThatThrownBy(() -> service.retry(41L))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("GENERATION_RETRY_NOT_ALLOWED"));
    }

    private AdminQuizService.QuizDetail detail(long quizId, LocalDate date, String status) {
        return new AdminQuizService.QuizDetail(new AdminQuizService.QuizSummary(
                quizId, date, "A", status, NOW, null), List.of());
    }
}

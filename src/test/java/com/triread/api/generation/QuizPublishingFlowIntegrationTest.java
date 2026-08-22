package com.triread.api.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triread.api.admin.AdminQuizService;
import com.triread.api.auth.AuthService;
import com.triread.api.common.ApiException;
import com.triread.api.prompt.PromptTemplateService;
import com.triread.api.quiz.QuizService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "app.quiz-generation.enabled=false",
        "app.quiz-generation.auto-publish=false",
        "app.quiz-generation.ai-validation-enabled=false",
        "app.quiz-generation.source-grounding-enabled=false",
        "app.quiz-generation.sets-per-date=1",
        "app.quiz-generation.max-api-calls-per-day=10",
        "app.quiz-generation.retry-delay-ms=0",
        "app.notifications.discord.enabled=false"
})
@Import(QuizPublishingFlowIntegrationTest.TestConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class QuizPublishingFlowIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2030, 1, 8);
    private static final LocalDate CONTENT_DATE = TODAY.minusDays(1);
    private static final Instant NOW = Instant.parse("2030-01-08T03:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("tri_read_generation_flow_test")
                    .withUsername("tri_read")
                    .withPassword("tri_read_test");

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    QuizGenerationService quizGenerationService;

    @Autowired
    AdminQuizService adminQuizService;

    @Autowired
    AuthService authService;

    @Autowired
    QuizService quizService;

    @Autowired
    FakeQuizAiGateway fakeQuizAiGateway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void generatesPublishesAndAssignsQuizUsingPostgres() {
        QuizGenerationService.GenerationResult generated =
                quizGenerationService.generate(CONTENT_DATE);

        long quizSetId = generated.quiz().quiz().quizSetId();
        assertThat(generated.status()).isEqualTo("READY");
        assertThat(generated.validationScore()).isGreaterThanOrEqualTo(90);
        assertThat(generated.quiz().quiz().status()).isEqualTo("REVIEWED");
        assertThat(fakeQuizAiGateway.generationCount()).isEqualTo(1);

        AdminQuizService.QuizDetail published = adminQuizService.publish(quizSetId);
        assertThat(published.quiz().status()).isEqualTo("PUBLISHED");

        AuthService.AuthenticatedUser reader =
                authService.register("publish.reader", "Publish Reader", "1234");
        QuizService.TodayQuizResponse todayQuiz = quizService.getTodayQuiz(reader.userId());

        assertThat(todayQuiz.quizSetId()).isEqualTo(quizSetId);
        assertThat(todayQuiz.challengeDate()).isEqualTo(TODAY);
        assertThat(todayQuiz.passages()).hasSize(3);
        assertThat(countRows("user_quiz_assignments")).isEqualTo(1);
        assertThat(countRows("ai_api_calls")).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT challenge_date FROM quiz_sets WHERE id = ?",
                LocalDate.class,
                quizSetId
        )).isEqualTo(CONTENT_DATE);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT study_date FROM user_quiz_assignments WHERE user_id = ?",
                LocalDate.class,
                reader.userId()
        )).isEqualTo(TODAY);

        assertThatThrownBy(() -> quizGenerationService.generate(CONTENT_DATE))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo("QUIZ_DATE_INVENTORY_FULL"));
        assertThat(fakeQuizAiGateway.generationCount()).isEqualTo(1);
    }

    private long countRows(String tableName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Long.class
        );
        return count == null ? 0 : count;
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        }

        @Bean
        @Primary
        FakeQuizAiGateway fakeQuizAiGateway() {
            return new FakeQuizAiGateway();
        }
    }

    static class FakeQuizAiGateway implements QuizAiGateway {
        private final AtomicInteger generationCount = new AtomicInteger();

        @Override
        public QuizGenerationData.GeneratedQuiz generate(
                LocalDate targetDate,
                List<QuizGenerationData.RecentPassageRow> recentPassages,
                PromptTemplateService.PromptSnapshot prompt
        ) {
            generationCount.incrementAndGet();
            QuizGenerationData.GeneratedQuiz fixture =
                    RuleBasedQuizValidatorTest.validGeneratedQuiz();
            return new QuizGenerationData.GeneratedQuiz(
                    targetDate,
                    fixture.passages()
            );
        }

        @Override
        public QuizGenerationData.SourceDiscovery discoverSources(
                LocalDate targetDate,
                List<QuizGenerationData.RecentPassageRow> recentPassages
        ) {
            throw new UnsupportedOperationException("Source discovery is disabled in this test");
        }

        @Override
        public QuizGenerationData.GeneratedQuiz generate(
                QuizGenerationData.SourceBrief sourceBrief,
                List<QuizGenerationData.RecentPassageRow> recentPassages,
                PromptTemplateService.PromptSnapshot prompt
        ) {
            return generate(sourceBrief.targetDate(), recentPassages, prompt);
        }

        @Override
        public QuizGenerationData.GeneratedQuiz repair(
                QuizGenerationData.GeneratedQuiz quiz,
                List<QuizValidation.Issue> issues,
                PromptTemplateService.PromptSnapshot prompt,
                QuizGenerationData.SourceBrief sourceBrief
        ) {
            return quiz;
        }

        @Override
        public QuizValidation.Result validate(
                QuizGenerationData.GeneratedQuiz quiz,
                PromptTemplateService.PromptSnapshot prompt
        ) {
            return new QuizValidation.Result(true, 100, List.of());
        }

        @Override
        public String provider() {
            return "TEST";
        }

        @Override
        public String generationModel() {
            return "test-generator";
        }

        @Override
        public String validationModel() {
            return "test-validator";
        }

        @Override
        public String sourceModel() {
            return "test-source";
        }

        int generationCount() {
            return generationCount.get();
        }
    }
}

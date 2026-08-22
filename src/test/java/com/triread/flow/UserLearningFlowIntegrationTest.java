package com.triread.flow;

import static org.assertj.core.api.Assertions.assertThat;

import com.triread.api.TriReadApiApplication;
import com.triread.api.auth.AuthService;
import com.triread.api.orbit.OrbitService;
import com.triread.api.quiz.QuizService;
import com.triread.api.review.AnswerReviewService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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

@SpringBootTest(classes = TriReadApiApplication.class, properties = {
        "app.quiz-generation.enabled=false",
        "app.notifications.discord.enabled=false"
})
@Import(UserLearningFlowIntegrationTest.FixedClockConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class UserLearningFlowIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2030, 1, 7);
    private static final Instant NOW = Instant.parse("2030-01-07T03:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("tri_read_flow_test")
                    .withUsername("tri_read")
                    .withPassword("tri_read_test");

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AuthService authService;

    @Autowired
    QuizService quizService;

    @Autowired
    AnswerReviewService answerReviewService;

    @Autowired
    OrbitService orbitService;

    @Test
    @Transactional
    void completesTheLearningFlowFromLoginToRecoveredRecord() {
        QuizFixture fixture = createPublishedQuiz();

        AuthService.AuthenticatedUser registered =
                authService.register("flow.reader", "Flow Reader", "1234");
        AuthService.AuthenticatedUser loggedIn =
                authService.login("FLOW.READER", "1234");

        assertThat(loggedIn.userId()).isEqualTo(registered.userId());

        QuizService.TodayQuizResponse todayQuiz = quizService.getTodayQuiz(loggedIn.userId());
        assertThat(todayQuiz.quizSetId()).isEqualTo(fixture.quizSetId());
        assertThat(todayQuiz.passages()).hasSize(3);

        List<QuizService.SubmittedAnswer> answers = new ArrayList<>();
        answers.add(new QuizService.SubmittedAnswer(
                fixture.questionIds().get(0), fixture.correctOptionIds().get(0)));
        answers.add(new QuizService.SubmittedAnswer(
                fixture.questionIds().get(1), fixture.correctOptionIds().get(1)));
        answers.add(new QuizService.SubmittedAnswer(
                fixture.questionIds().get(2), fixture.wrongOptionIds().get(2)));

        QuizService.QuizResultResponse result =
                quizService.submitAttempt(loggedIn.userId(), fixture.quizSetId(), answers);

        assertThat(result.score()).isEqualTo(2);
        assertThat(result.wrongCount()).isEqualTo(1);
        assertThat(result.attemptType()).isEqualTo("PRIMARY");

        AnswerReviewService.ReviewListResponse openReviews =
                answerReviewService.getReviews(loggedIn.userId(), "OPEN");
        assertThat(openReviews.openCount()).isEqualTo(1);
        assertThat(openReviews.reviews()).singleElement().satisfies(review -> {
            assertThat(review.passageContent()).contains("Passage 1");
            assertThat(review.status()).isEqualTo("PENDING");
        });

        OrbitService.OrbitDay recoveringDay = findToday(
                orbitService.getOrbit(loggedIn.userId(), "WEEK", TODAY));
        assertThat(recoveringDay.status()).isEqualTo("RECOVERING");
        assertThat(recoveringDay.brightness()).isZero();

        long reviewId = openReviews.reviews().get(0).reviewId();
        answerReviewService.updateStatus(loggedIn.userId(), reviewId, "RECOVERED");

        OrbitService.OrbitDay recoveredDay = findToday(
                orbitService.getOrbit(loggedIn.userId(), "WEEK", TODAY));
        assertThat(recoveredDay.status()).isEqualTo("LIT");
        assertThat(recoveredDay.brightness()).isEqualTo(100);
        assertThat(recoveredDay.recoveredCount()).isEqualTo(1);
    }

    private QuizFixture createPublishedQuiz() {
        long quizSetId = jdbcTemplate.queryForObject(
                "INSERT INTO quiz_sets "
                        + "(challenge_date, available_on, status, variant_code, published_at) "
                        + "VALUES (?, ?, 'PUBLISHED', 'A', ?) RETURNING id",
                Long.class,
                TODAY,
                TODAY,
                Timestamp.from(NOW)
        );

        List<Long> firstPassageQuestionIds = new ArrayList<>();
        List<Long> firstPassageCorrectOptionIds = new ArrayList<>();
        List<Long> firstPassageWrongOptionIds = new ArrayList<>();

        for (int passagePosition = 1; passagePosition <= 3; passagePosition++) {
            long passageId = insertPassage(quizSetId, passagePosition);
            for (int questionPosition = 1; questionPosition <= 3; questionPosition++) {
                long questionId = insertQuestion(passageId, questionPosition);
                List<Long> optionIds = insertOptions(questionId);
                insertAnswerKey(questionId, optionIds.get(0));

                if (passagePosition == 1) {
                    firstPassageQuestionIds.add(questionId);
                    firstPassageCorrectOptionIds.add(optionIds.get(0));
                    firstPassageWrongOptionIds.add(optionIds.get(1));
                }
            }
        }

        return new QuizFixture(
                quizSetId,
                firstPassageQuestionIds,
                firstPassageCorrectOptionIds,
                firstPassageWrongOptionIds
        );
    }

    private long insertPassage(long quizSetId, int position) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO passages (quiz_set_id, position, title, topic, content) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                quizSetId,
                position,
                "Passage " + position,
                "Topic " + position,
                "Passage " + position + " content for the integration test."
        );
    }

    private long insertQuestion(long passageId, int position) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO questions (passage_id, position, content) "
                        + "VALUES (?, ?, ?) RETURNING id",
                Long.class,
                passageId,
                position,
                "Question " + position
        );
    }

    private List<Long> insertOptions(long questionId) {
        List<Long> optionIds = new ArrayList<>();
        for (int position = 1; position <= 4; position++) {
            long optionId = jdbcTemplate.queryForObject(
                    "INSERT INTO question_options (question_id, position, content) "
                            + "VALUES (?, ?, ?) RETURNING id",
                    Long.class,
                    questionId,
                    position,
                    "Option " + position
            );
            optionIds.add(optionId);
        }
        return optionIds;
    }

    private void insertAnswerKey(long questionId, long correctOptionId) {
        jdbcTemplate.update(
                "INSERT INTO question_keys "
                        + "(question_id, correct_option_id, explanation, evidence) "
                        + "VALUES (?, ?, ?, ?)",
                questionId,
                correctOptionId,
                "The first option is correct.",
                "integration test"
        );
    }

    private OrbitService.OrbitDay findToday(OrbitService.OrbitResponse orbit) {
        for (OrbitService.OrbitDay day : orbit.days()) {
            if (TODAY.equals(day.date())) {
                return day;
            }
        }
        throw new AssertionError("Today's learning record was not found");
    }

    record QuizFixture(
            long quizSetId,
            List<Long> questionIds,
            List<Long> correctOptionIds,
            List<Long> wrongOptionIds
    ) {
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        }
    }
}

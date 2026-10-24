package com.triread.api.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.triread.api.TriReadApiApplication;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
        "app.quiz-generation.enabled=false", "app.notifications.discord.enabled=false"
})
@Import(ContentInventoryIntegrationTest.FixedClockConfig.class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ContentInventoryIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2030, 1, 7);
    private static final Instant NOW = Instant.parse("2030-01-07T03:00:00Z");
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-bookworm")
            .withDatabaseName("inventory_test").withUsername("inventory").withPassword("test-only");

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ContentInventoryService service;
    private int sequence;

    @Test
    void countsPerUserUnassignedAndCarryForwardStockWithoutCountingCompletedBonuses() {
        long reader = user("reader", "USER", true, NOW);
        long learner = user("learner", "USER", true, NOW.minusSeconds(31 * 86400L));
        user("admin", "ADMIN", true, NOW);
        user("inactive", "USER", true, NOW.minusSeconds(31 * 86400L));
        user("disabled", "USER", false, NOW);

        long completed = quiz("PUBLISHED", TODAY.minusDays(3), NOW, false);
        long unstarted = quiz("PUBLISHED", TODAY.minusDays(2), NOW, false);
        quiz("PUBLISHED", TODAY, NOW, false);
        quiz("PUBLISHED", TODAY, NOW, false);
        quiz("PUBLISHED", TODAY.plusDays(1), NOW, false);
        quiz("PUBLISHED", TODAY, NOW, true);
        quiz("DRAFT", TODAY, NOW, false);
        quiz("REVIEWED", TODAY, NOW, false);
        assign(reader, completed, TODAY.minusDays(2));
        complete(reader, completed);
        assign(reader, unstarted, TODAY.minusDays(1));
        assign(learner, completed, TODAY);
        complete(learner, completed); // Recent learning activates a user even without recent login.

        OperationsData.ContentInventory result = service.inventory();
        assertThat(result.users().activeUserCount()).isEqualTo(3);
        assertThat(result.users().minimumRemainingSets()).isEqualTo(3);
        assertThat(result.pool().availablePublishedCount()).isEqualTo(4);
        assertThat(result.pool().scheduledPublishedCount()).isEqualTo(1);
        assertThat(result.pool().retiredPublishedCount()).isEqualTo(1);
        assertThat(result.lowestStockUsers()).filteredOn(u -> u.userId() == reader)
                .singleElement().satisfies(stock -> {
                    assertThat(stock.unassignedSetCount()).isEqualTo(2);
                    assertThat(stock.unstartedSetCount()).isEqualTo(1);
                    assertThat(stock.remainingSetCount()).isEqualTo(3);
                });
        assertThat(result.lowestStockUsers()).extracting(OperationsData.UserInventory::loginName)
                .containsExactly("reader", "learner", "admin");
    }

    @Test
    void overdueQueueUsesCreationAgeAndAvailabilityAndExcludesRetiredOrFutureSets() {
        Instant cutoff = NOW.minusSeconds(48 * 3600L);
        quiz("DRAFT", TODAY, cutoff, false);
        quiz("REVIEWED", TODAY.minusDays(1), cutoff.minusSeconds(1), false);
        quiz("DRAFT", TODAY, cutoff.plusSeconds(1), false);
        quiz("REVIEWED", TODAY.plusDays(1), cutoff.minusSeconds(1), false);
        quiz("DRAFT", TODAY, cutoff.minusSeconds(1), true);

        OperationsData.ContentInventory result = service.inventory();
        assertThat(result.pool().draftCount()).isEqualTo(2);
        assertThat(result.pool().reviewedCount()).isEqualTo(2);
        assertThat(result.pool().overduePendingCount()).isEqualTo(2);
        assertThat(result.pool().oldestOverduePendingAt()).isEqualTo(cutoff.minusSeconds(1));
        assertThat(result.status()).isEqualTo("NO_ACTIVE_USERS");
        assertThat(result.users().minimumRemainingSets()).isNull();
        assertThat(result.alerts()).extracting(OperationsData.InventoryAlert::code)
                .containsExactly("OVERDUE_PUBLICATION");
    }

    @Test
    void minimumCoversAllActiveUsersWhileDetailsAreBounded() {
        long available = quiz("PUBLISHED", TODAY, NOW, false);
        for (int i = 0; i < 23; i++) {
            long id = user("user" + i, "USER", true, NOW);
            if (i == 22) {
                assign(id, available, TODAY);
                complete(id, available);
            }
        }
        OperationsData.ContentInventory result = service.inventory();
        assertThat(result.users().activeUserCount()).isEqualTo(23);
        assertThat(result.users().minimumRemainingSets()).isZero();
        assertThat(result.lowestStockUsers()).hasSize(20);
        assertThat(result.lowestStockUsers().getFirst().loginName()).isEqualTo("user22");
    }

    @Test
    void currentUnstartedAssignmentIsCountedOnlyOnce() {
        long id = user("today", "USER", true, NOW);
        long set = quiz("PUBLISHED", TODAY, NOW, false);
        assign(id, set, TODAY);
        OperationsData.UserInventory stock = service.inventory().lowestStockUsers().getFirst();
        assertThat(stock.unassignedSetCount()).isZero();
        assertThat(stock.unstartedSetCount()).isEqualTo(1);
        assertThat(stock.remainingSetCount()).isEqualTo(1);
    }

    private long user(String login, String role, boolean enabled, Instant lastLogin) {
        return jdbc.queryForObject("""
                INSERT INTO app_users (login_name, display_name, pin_hash, app_role, enabled, last_login_at)
                VALUES (?, ?, 'test-only-hash', ?, ?, ?) RETURNING id
                """, Long.class, login, login, role, enabled, Timestamp.from(lastLogin));
    }

    private long quiz(String status, LocalDate availableOn, Instant createdAt, boolean retired) {
        return jdbc.queryForObject("""
                INSERT INTO quiz_sets (challenge_date, available_on, status, created_at, retired_at)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, TODAY.minusYears(2).plusDays(++sequence), availableOn,
                status, Timestamp.from(createdAt), retired ? Timestamp.from(NOW) : null);
    }

    private void assign(long userId, long quizId, LocalDate date) {
        jdbc.update("""
                INSERT INTO user_quiz_assignments (user_id, quiz_set_id, challenge_date, study_date)
                VALUES (?, ?, ?, ?)
                """, userId, quizId, date, date);
    }

    private void complete(long userId, long quizId) {
        long passageId = jdbc.queryForObject("""
                INSERT INTO passages (quiz_set_id, position, content)
                VALUES (?, 1, 'Test passage') ON CONFLICT (quiz_set_id, position)
                DO UPDATE SET content = EXCLUDED.content RETURNING id
                """, Long.class, quizId);
        jdbc.update("""
                INSERT INTO quiz_attempts (user_id, quiz_set_id, passage_id, attempt_type, score, completed_at)
                VALUES (?, ?, ?, 'PRIMARY', 3, ?)
                """, userId, quizId, passageId, Timestamp.from(NOW));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean @Primary
        Clock fixedClock() { return Clock.fixed(NOW, ZoneId.of("Asia/Seoul")); }
    }
}

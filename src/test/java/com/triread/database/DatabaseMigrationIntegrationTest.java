package com.triread.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("tri_read_test")
                    .withUsername("tri_read")
                    .withPassword("tri_read_test");

    @Test
    void appliesEveryMigrationToAnEmptyPostgresDatabase() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        assertThat(flyway.migrate().success).isTrue();
        assertThat(tableExists("app_users")).isTrue();
        assertThat(tableExists("study_groups")).isTrue();
        assertThat(tableExists("prompt_templates")).isTrue();
        assertThat(tableExists("prompt_activations")).isTrue();
        assertThat(tableExists("admin_audit_logs")).isTrue();
        assertThat(columnExists("quiz_sets", "available_on")).isTrue();
        assertThat(columnExists("quiz_sets", "retired_at")).isTrue();
        assertThat(columnExists("user_quiz_assignments", "study_date")).isTrue();
        assertThat(readInventoryDate()).isEqualTo(LocalDate.of(2026, 7, 20));
    }

    private boolean columnExists(String tableName, String columnName) throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                             + "WHERE table_schema = 'public' AND table_name = ? "
                             + "AND column_name = ?)")
        ) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private boolean tableExists(String tableName) throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                             + "WHERE table_schema = 'public' AND table_name = ?)")
        ) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private LocalDate readInventoryDate() throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT days.challenge_date::DATE AS challenge_date "
                             + "FROM generate_series(?::DATE, ?::DATE, INTERVAL '1 day') "
                             + "AS days(challenge_date) "
                             + "ORDER BY days.challenge_date::DATE LIMIT 1")
        ) {
            statement.setObject(1, LocalDate.of(2026, 7, 20));
            statement.setObject(2, LocalDate.of(2026, 7, 24));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getObject("challenge_date", LocalDate.class);
            }
        }
    }
}

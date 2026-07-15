package com.triread.api.orbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OrbitServiceImplTest {
    private static final long USER_ID = 3L;

    @Mock
    private OrbitMapper orbitMapper;

    private OrbitService orbitService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-08T03:00:00Z"), ZoneOffset.UTC);
        orbitService = new OrbitServiceImpl(orbitMapper, clock);
    }

    @Test
    void weekShowsPerfectAndRecoveredAttemptsAsFullyLit() {
        LocalDate monday = LocalDate.of(2026, 7, 6);
        LocalDate friday = LocalDate.of(2026, 7, 10);
        LocalDate sunday = LocalDate.of(2026, 7, 12);
        when(orbitMapper.findAttempts(USER_ID, monday, sunday)).thenReturn(List.of(
                new OrbitData.OrbitAttemptRow(monday, 9, 0, 0),
                new OrbitData.OrbitAttemptRow(monday.plusDays(1), 7, 2, 1),
                new OrbitData.OrbitAttemptRow(monday.plusDays(2), 6, 3, 3)
        ));

        OrbitService.OrbitResponse result = orbitService.getOrbit(USER_ID, "week", null);

        assertThat(result.days()).hasSize(5);
        assertThat(result.completedDays()).isEqualTo(3);
        assertThat(result.fullyLitDays()).isEqualTo(2);
        assertThat(result.days().get(0).brightness()).isEqualTo(100);
        assertThat(result.days().get(1).brightness()).isEqualTo(50);
        assertThat(result.days().get(1).status()).isEqualTo("RECOVERING");
        assertThat(result.days().get(2).status()).isEqualTo("LIT");
        assertThat(result.days().get(3).status()).isEqualTo("EMPTY");
        verify(orbitMapper).findAttempts(USER_ID, monday, sunday);
    }

    @Test
    void monthExcludesWeekends() {
        LocalDate anchor = LocalDate.of(2026, 7, 20);
        when(orbitMapper.findAttempts(USER_ID, LocalDate.of(2026, 6, 29),
                LocalDate.of(2026, 8, 2))).thenReturn(List.of());

        OrbitService.OrbitResponse result = orbitService.getOrbit(USER_ID, "MONTH", anchor);

        assertThat(result.days()).hasSize(23);
        assertThat(result.days()).noneMatch(day -> day.date().getDayOfWeek().getValue() > 5);
    }

    @Test
    void weekendAnchorStillUsesTheCurrentMondayToFriday() {
        LocalDate saturday = LocalDate.of(2026, 7, 11);
        LocalDate monday = LocalDate.of(2026, 7, 6);
        LocalDate friday = LocalDate.of(2026, 7, 10);
        LocalDate sunday = LocalDate.of(2026, 7, 12);
        when(orbitMapper.findAttempts(USER_ID, monday, sunday)).thenReturn(List.of());

        OrbitService.OrbitResponse result = orbitService.getOrbit(USER_ID, "WEEK", saturday);

        assertThat(result.startDate()).isEqualTo(monday);
        assertThat(result.endDate()).isEqualTo(friday);
        assertThat(result.days()).hasSize(5);
    }

    @Test
    void weekendAttemptsFillTheEarliestMissingWeekdays() {
        LocalDate monday = LocalDate.of(2026, 7, 6);
        LocalDate sunday = LocalDate.of(2026, 7, 12);
        when(orbitMapper.findAttempts(USER_ID, monday, sunday)).thenReturn(List.of(
                new OrbitData.OrbitAttemptRow(monday, 8, 1, 1),
                new OrbitData.OrbitAttemptRow(monday.plusDays(2), 9, 0, 0),
                new OrbitData.OrbitAttemptRow(monday.plusDays(5), 7, 2, 2),
                new OrbitData.OrbitAttemptRow(sunday, 6, 3, 1)
        ));

        OrbitService.OrbitResponse result = orbitService.getOrbit(
                USER_ID, "WEEK", LocalDate.of(2026, 7, 11)
        );

        assertThat(result.completedDays()).isEqualTo(4);
        assertThat(result.days().get(1).weekendMakeUp()).isTrue();
        assertThat(result.days().get(1).sourceDate()).isEqualTo(monday.plusDays(5));
        assertThat(result.days().get(3).weekendMakeUp()).isTrue();
        assertThat(result.days().get(3).sourceDate()).isEqualTo(sunday);
        assertThat(result.days().get(4).status()).isEqualTo("EMPTY");
    }

    @Test
    void streakSkipsTheWeekendAndStaysActiveBeforeTodaysQuiz() {
        LocalDate firstAttempt = LocalDate.of(2026, 7, 3);
        LocalDate queryStart = LocalDate.of(2026, 6, 29);
        LocalDate queryEnd = LocalDate.of(2026, 7, 12);
        when(orbitMapper.findFirstAttemptDate(USER_ID)).thenReturn(firstAttempt);
        when(orbitMapper.findAttempts(USER_ID, queryStart, queryEnd)).thenReturn(List.of(
                new OrbitData.OrbitAttemptRow(firstAttempt, 9, 0, 0),
                new OrbitData.OrbitAttemptRow(LocalDate.of(2026, 7, 6), 8, 1, 1),
                new OrbitData.OrbitAttemptRow(LocalDate.of(2026, 7, 7), 7, 2, 2)
        ));

        OrbitService.StreakResponse result = orbitService.getStreak(USER_ID);

        assertThat(result.currentStreak()).isEqualTo(3);
        assertThat(result.completedToday()).isFalse();
    }

    @Test
    void streakIncludesTodayAfterCompletingTheQuiz() {
        LocalDate monday = LocalDate.of(2026, 7, 6);
        LocalDate sunday = LocalDate.of(2026, 7, 12);
        when(orbitMapper.findFirstAttemptDate(USER_ID)).thenReturn(monday);
        when(orbitMapper.findAttempts(USER_ID, monday, sunday)).thenReturn(List.of(
                new OrbitData.OrbitAttemptRow(monday, 9, 0, 0),
                new OrbitData.OrbitAttemptRow(monday.plusDays(1), 8, 1, 1),
                new OrbitData.OrbitAttemptRow(monday.plusDays(2), 7, 2, 2)
        ));

        OrbitService.StreakResponse result = orbitService.getStreak(USER_ID);

        assertThat(result.currentStreak()).isEqualTo(3);
        assertThat(result.completedToday()).isTrue();
    }

    @Test
    void streakIsEmptyWhenTheUserHasNoAttempts() {
        when(orbitMapper.findFirstAttemptDate(USER_ID)).thenReturn(null);

        OrbitService.StreakResponse result = orbitService.getStreak(USER_ID);

        assertThat(result.currentStreak()).isZero();
        assertThat(result.completedToday()).isFalse();
    }

    @Test
    void rejectsUnknownPeriod() {
        assertThatThrownBy(() -> orbitService.getOrbit(USER_ID, "YEAR", null))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("INVALID_ORBIT_PERIOD");
                });
    }
}

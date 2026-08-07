package com.triread.api.orbit;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrbitServiceImpl implements OrbitService {
    private static final Set<String> PERIODS = Set.of("WEEK", "MONTH");

    private final OrbitMapper orbitMapper;
    private final Clock clock;

    public OrbitServiceImpl(OrbitMapper orbitMapper, Clock clock) {
        this.orbitMapper = orbitMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public OrbitResponse getOrbit(long userId, String rawPeriod, LocalDate rawAnchorDate) {
        String period = normalizePeriod(rawPeriod);
        LocalDate anchorDate = rawAnchorDate == null ? LocalDate.now(clock) : rawAnchorDate;
        LocalDate startDate = startDate(period, anchorDate);
        LocalDate endDate = endDate(period, anchorDate);
        LocalDate queryStart = startDate.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );
        LocalDate queryEnd = endDate.with(
                TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)
        );
        List<OrbitData.OrbitAttemptRow> attempts = orbitMapper.findAttempts(
                userId, queryStart, queryEnd
        );
        Map<LocalDate, OrbitData.OrbitAttemptRow> assignedAttempts = assignWeekendAttempts(
                queryStart, queryEnd, attempts
        );

        List<OrbitDay> days = createOrbitDays(startDate, endDate, assignedAttempts);
        int completedDays = countCompletedDays(days);
        int fullyLitDays = countFullyLitDays(days);

        return new OrbitResponse(period, startDate, endDate, completedDays, fullyLitDays, days);
    }

    @Override
    @Transactional(readOnly = true)
    public StreakResponse getStreak(long userId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate firstAttemptDate = orbitMapper.findFirstAttemptDate(userId);
        if (firstAttemptDate == null) {
            return new StreakResponse(0, false);
        }

        LocalDate queryStart = weekMonday(firstAttemptDate);
        LocalDate queryEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<OrbitData.OrbitAttemptRow> attempts = orbitMapper.findAttempts(
                userId, queryStart, queryEnd
        );
        Map<LocalDate, OrbitData.OrbitAttemptRow> assignedAttempts = assignWeekendAttempts(
                queryStart, queryEnd, attempts
        );
        boolean completedToday = hasCompletedOn(today, attempts);
        LocalDate cursor = isWeekday(today) && assignedAttempts.containsKey(today)
                ? today
                : previousWeekday(today);
        int currentStreak = 0;
        while (assignedAttempts.containsKey(cursor)) {
            currentStreak++;
            cursor = previousWeekday(cursor);
        }

        return new StreakResponse(currentStreak, completedToday);
    }

    private List<OrbitDay> createOrbitDays(
            LocalDate startDate,
            LocalDate endDate,
            Map<LocalDate, OrbitData.OrbitAttemptRow> assignedAttempts
    ) {
        List<OrbitDay> days = new ArrayList<>();
        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            if (isWeekday(date)) {
                days.add(toOrbitDay(date, assignedAttempts.get(date)));
            }
            date = date.plusDays(1);
        }
        return days;
    }

    private int countCompletedDays(List<OrbitDay> days) {
        int count = 0;
        for (OrbitDay day : days) {
            if (day.score() != null) {
                count++;
            }
        }
        return count;
    }

    private int countFullyLitDays(List<OrbitDay> days) {
        int count = 0;
        for (OrbitDay day : days) {
            if ("LIT".equals(day.status())) {
                count++;
            }
        }
        return count;
    }

    private boolean hasCompletedOn(
            LocalDate date,
            List<OrbitData.OrbitAttemptRow> attempts
    ) {
        for (OrbitData.OrbitAttemptRow attempt : attempts) {
            if (date.equals(attempt.completedDate())) {
                return true;
            }
        }
        return false;
    }

    private OrbitDay toOrbitDay(LocalDate date, OrbitData.OrbitAttemptRow attempt) {
        if (attempt == null) {
            return new OrbitDay(date, null, false, "EMPTY", 0, null, 0, 0);
        }
        int brightness = attempt.wrongCount() == 0
                ? 100
                : Math.min(100, attempt.recoveredCount() * 100 / attempt.wrongCount());
        String status = brightness == 100 ? "LIT" : "RECOVERING";
        boolean weekendMakeUp = !date.equals(attempt.challengeDate());
        return new OrbitDay(date, attempt.challengeDate(), weekendMakeUp,
                status, brightness, attempt.score(),
                attempt.wrongCount(), attempt.recoveredCount());
    }

    private Map<LocalDate, OrbitData.OrbitAttemptRow> assignWeekendAttempts(
            LocalDate queryStart,
            LocalDate queryEnd,
            List<OrbitData.OrbitAttemptRow> attempts
    ) {
        Map<LocalDate, OrbitData.OrbitAttemptRow> assigned = new LinkedHashMap<>();
        Map<LocalDate, List<OrbitData.OrbitAttemptRow>> weekendAttemptsByWeek = new LinkedHashMap<>();
        for (OrbitData.OrbitAttemptRow attempt : attempts) {
            if (isWeekday(attempt.challengeDate())) {
                assigned.put(attempt.challengeDate(), attempt);
            } else {
                addWeekendAttempt(weekendAttemptsByWeek, attempt);
            }
        }

        for (Map.Entry<LocalDate, List<OrbitData.OrbitAttemptRow>> entry
                : weekendAttemptsByWeek.entrySet()) {
            entry.getValue().sort((first, second) ->
                    first.challengeDate().compareTo(second.challengeDate()));
            List<LocalDate> emptyWeekdays = findEmptyWeekdays(
                    entry.getKey(), queryStart, queryEnd, assigned);
            int assignmentCount = Math.min(emptyWeekdays.size(), entry.getValue().size());
            for (int index = 0; index < assignmentCount; index++) {
                assigned.put(emptyWeekdays.get(index), entry.getValue().get(index));
            }
        }
        return assigned;
    }

    private void addWeekendAttempt(
            Map<LocalDate, List<OrbitData.OrbitAttemptRow>> attemptsByWeek,
            OrbitData.OrbitAttemptRow attempt
    ) {
        LocalDate monday = weekMonday(attempt.challengeDate());
        List<OrbitData.OrbitAttemptRow> weekAttempts = attemptsByWeek.get(monday);
        if (weekAttempts == null) {
            weekAttempts = new ArrayList<>();
            attemptsByWeek.put(monday, weekAttempts);
        }
        weekAttempts.add(attempt);
    }

    private List<LocalDate> findEmptyWeekdays(
            LocalDate monday,
            LocalDate queryStart,
            LocalDate queryEnd,
            Map<LocalDate, OrbitData.OrbitAttemptRow> assigned
    ) {
        List<LocalDate> emptyWeekdays = new ArrayList<>();
        for (int day = 0; day < 5; day++) {
            LocalDate date = monday.plusDays(day);
            boolean insideQuery = !date.isBefore(queryStart) && !date.isAfter(queryEnd);
            if (insideQuery && !assigned.containsKey(date)) {
                emptyWeekdays.add(date);
            }
        }
        return emptyWeekdays;
    }

    private LocalDate weekMonday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private LocalDate previousWeekday(LocalDate date) {
        LocalDate previous = date.minusDays(1);
        while (!isWeekday(previous)) {
            previous = previous.minusDays(1);
        }
        return previous;
    }

    private String normalizePeriod(String rawPeriod) {
        String period = rawPeriod == null ? "WEEK" : rawPeriod.trim().toUpperCase(Locale.ROOT);
        if (!PERIODS.contains(period)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORBIT_PERIOD",
                    "Orbit period must be WEEK or MONTH.");
        }
        return period;
    }

    private LocalDate startDate(String period, LocalDate anchorDate) {
        return "MONTH".equals(period)
                ? YearMonth.from(anchorDate).atDay(1)
                : anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private LocalDate endDate(String period, LocalDate anchorDate) {
        return "MONTH".equals(period)
                ? YearMonth.from(anchorDate).atEndOfMonth()
                : startDate(period, anchorDate).plusDays(4);
    }

    private boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}

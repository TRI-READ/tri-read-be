package com.triread.api.orbit;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

        List<OrbitDay> days = startDate.datesUntil(endDate.plusDays(1))
                .filter(this::isWeekday)
                .map(date -> toOrbitDay(date, assignedAttempts.get(date)))
                .toList();
        int completedDays = (int) days.stream().filter(day -> day.score() != null).count();
        int fullyLitDays = (int) days.stream().filter(day -> "LIT".equals(day.status())).count();

        return new OrbitResponse(period, startDate, endDate, completedDays, fullyLitDays, days);
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
        attempts.stream()
                .filter(attempt -> isWeekday(attempt.challengeDate()))
                .forEach(attempt -> assigned.put(attempt.challengeDate(), attempt));

        Map<LocalDate, List<OrbitData.OrbitAttemptRow>> weekendAttemptsByWeek = new LinkedHashMap<>();
        attempts.stream()
                .filter(attempt -> !isWeekday(attempt.challengeDate()))
                .sorted(Comparator.comparing(OrbitData.OrbitAttemptRow::challengeDate))
                .forEach(attempt -> weekendAttemptsByWeek
                        .computeIfAbsent(weekMonday(attempt.challengeDate()), ignored -> new ArrayList<>())
                        .add(attempt));

        for (Map.Entry<LocalDate, List<OrbitData.OrbitAttemptRow>> entry
                : weekendAttemptsByWeek.entrySet()) {
            List<LocalDate> emptyWeekdays = entry.getKey().datesUntil(entry.getKey().plusDays(5))
                    .filter(date -> !date.isBefore(queryStart) && !date.isAfter(queryEnd))
                    .filter(date -> !assigned.containsKey(date))
                    .toList();
            for (int index = 0; index < Math.min(emptyWeekdays.size(), entry.getValue().size()); index++) {
                assigned.put(emptyWeekdays.get(index), entry.getValue().get(index));
            }
        }
        return assigned;
    }

    private LocalDate weekMonday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
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

package com.triread.api.orbit;

import java.time.LocalDate;
import java.util.List;

public interface OrbitService {
    OrbitResponse getOrbit(long userId, String period, LocalDate anchorDate);

    record OrbitResponse(
            String period,
            LocalDate startDate,
            LocalDate endDate,
            int completedDays,
            int fullyLitDays,
            List<OrbitDay> days
    ) {
    }

    record OrbitDay(
            LocalDate date,
            LocalDate sourceDate,
            boolean weekendMakeUp,
            String status,
            int brightness,
            Integer score,
            int wrongCount,
            int recoveredCount
    ) {
    }
}

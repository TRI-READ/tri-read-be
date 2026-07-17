package com.triread.api.orbit;

import java.time.LocalDate;

public final class OrbitData {
    private OrbitData() {
    }

    public record OrbitAttemptRow(
            LocalDate challengeDate,
            LocalDate completedDate,
            int score,
            int wrongCount,
            int recoveredCount
    ) {
        public OrbitAttemptRow(
                LocalDate challengeDate,
                int score,
                int wrongCount,
                int recoveredCount
        ) {
            this(challengeDate, challengeDate, score, wrongCount, recoveredCount);
        }
    }
}

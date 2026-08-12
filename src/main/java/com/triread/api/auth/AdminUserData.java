package com.triread.api.auth;

import java.time.Instant;
import java.time.LocalDate;

public final class AdminUserData {
    private AdminUserData() {}

    public record ActivityStatsRow(long totalAttempts, long learningDays,
                                   double averageScore, Instant lastCompletedAt) {}

    public record RecentAttemptRow(long attemptId, long quizSetId, long passageId,
                                   LocalDate challengeDate, String passageTitle,
                                   String attemptType, int score, Instant completedAt) {}
}

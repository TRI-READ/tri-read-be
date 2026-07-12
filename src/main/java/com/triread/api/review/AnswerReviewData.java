package com.triread.api.review;

import java.time.Instant;
import java.time.LocalDate;

public final class AnswerReviewData {

    private AnswerReviewData() {
    }

    public record ReviewSummaryRow(
            int totalCount,
            int openCount,
            int recoveredCount
    ) {
    }

    public record ReviewRow(
            long reviewId,
            long questionId,
            String reviewStatus,
            int retryCount,
            LocalDate challengeDate,
            String passageTitle,
            String passageTopic,
            short questionPosition,
            String questionContent,
            long selectedOptionId,
            short selectedOptionPosition,
            String selectedOptionContent,
            long correctOptionId,
            short correctOptionPosition,
            String correctOptionContent,
            String explanation,
            String evidence,
            Instant createdAt,
            Instant lastReviewedAt,
            Instant recoveredAt
    ) {
    }
}

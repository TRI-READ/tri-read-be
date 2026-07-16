package com.triread.api.review;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface AnswerReviewService {

    ReviewListResponse getReviews(long userId, String filter);

    ReviewItem getReview(long userId, long reviewId);

    ReviewItem updateStatus(long userId, long reviewId, String status);

    record ReviewListResponse(
            int totalCount,
            int openCount,
            int recoveredCount,
            List<ReviewItem> reviews
    ) {
    }

    record ReviewItem(
            long reviewId,
            long questionId,
            String status,
            int retryCount,
            LocalDate challengeDate,
            String passageTitle,
            String passageTopic,
            String passageContent,
            short questionPosition,
            String questionContent,
            ReviewOption selectedOption,
            ReviewOption correctOption,
            String explanation,
            String evidence,
            Instant createdAt,
            Instant lastReviewedAt,
            Instant recoveredAt
    ) {
        static ReviewItem from(AnswerReviewData.ReviewRow row) {
            return new ReviewItem(
                    row.reviewId(),
                    row.questionId(),
                    row.reviewStatus(),
                    row.retryCount(),
                    row.challengeDate(),
                    row.passageTitle(),
                    row.passageTopic(),
                    row.passageContent(),
                    row.questionPosition(),
                    row.questionContent(),
                    new ReviewOption(
                            row.selectedOptionId(),
                            row.selectedOptionPosition(),
                            row.selectedOptionContent()
                    ),
                    new ReviewOption(
                            row.correctOptionId(),
                            row.correctOptionPosition(),
                            row.correctOptionContent()
                    ),
                    row.explanation(),
                    row.evidence(),
                    row.createdAt(),
                    row.lastReviewedAt(),
                    row.recoveredAt()
            );
        }
    }

    record ReviewOption(
            long optionId,
            short position,
            String content
    ) {
    }
}

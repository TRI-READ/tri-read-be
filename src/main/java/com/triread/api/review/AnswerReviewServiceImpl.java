package com.triread.api.review;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnswerReviewServiceImpl implements AnswerReviewService {

    private static final Set<String> FILTERS = Set.of("OPEN", "RECOVERED", "ALL");
    private static final Set<String> UPDATABLE_STATUSES = Set.of("PENDING", "RECOVERED");

    private final AnswerReviewMapper answerReviewMapper;
    private final Clock clock;

    public AnswerReviewServiceImpl(AnswerReviewMapper answerReviewMapper, Clock clock) {
        this.answerReviewMapper = answerReviewMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewListResponse getReviews(long userId, String rawFilter) {
        String filter = normalizeFilter(rawFilter);
        AnswerReviewData.ReviewSummaryRow summary = answerReviewMapper.findSummary(userId);
        List<ReviewItem> reviews = answerReviewMapper.findReviews(userId, filter).stream()
                .map(ReviewItem::from)
                .toList();

        return new ReviewListResponse(
                summary.totalCount(),
                summary.openCount(),
                summary.recoveredCount(),
                reviews
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewItem getReview(long userId, long reviewId) {
        return ReviewItem.from(requireReview(userId, reviewId));
    }

    @Override
    @Transactional
    public ReviewItem updateStatus(long userId, long reviewId, String rawStatus) {
        String status = normalizeStatus(rawStatus);
        requireReview(userId, reviewId);
        answerReviewMapper.updateStatus(userId, reviewId, status, clock.instant());
        return ReviewItem.from(requireReview(userId, reviewId));
    }

    private AnswerReviewData.ReviewRow requireReview(long userId, long reviewId) {
        AnswerReviewData.ReviewRow review = answerReviewMapper.findReview(userId, reviewId);
        if (review == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "ANSWER_REVIEW_NOT_FOUND",
                    "The answer review was not found."
            );
        }
        return review;
    }

    private String normalizeFilter(String rawFilter) {
        String filter = rawFilter == null ? "OPEN" : rawFilter.trim().toUpperCase(Locale.ROOT);
        if (!FILTERS.contains(filter)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REVIEW_FILTER",
                    "Review filter must be OPEN, RECOVERED, or ALL."
            );
        }
        return filter;
    }

    private String normalizeStatus(String rawStatus) {
        String status = rawStatus == null ? "" : rawStatus.trim().toUpperCase(Locale.ROOT);
        if (!UPDATABLE_STATUSES.contains(status)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REVIEW_STATUS",
                    "Review status must be PENDING or RECOVERED."
            );
        }
        return status;
    }
}

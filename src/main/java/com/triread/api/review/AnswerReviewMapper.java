package com.triread.api.review;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AnswerReviewMapper {

    AnswerReviewData.ReviewSummaryRow findSummary(long userId);

    List<AnswerReviewData.ReviewRow> findReviews(
            @Param("userId") long userId,
            @Param("filter") String filter
    );

    AnswerReviewData.ReviewRow findReview(
            @Param("userId") long userId,
            @Param("reviewId") long reviewId
    );

    int updateStatus(
            @Param("userId") long userId,
            @Param("reviewId") long reviewId,
            @Param("status") String status,
            @Param("reviewedAt") Instant reviewedAt
    );
}

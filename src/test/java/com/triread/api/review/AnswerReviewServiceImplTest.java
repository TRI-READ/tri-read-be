package com.triread.api.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
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
class AnswerReviewServiceImplTest {

    private static final long USER_ID = 3L;
    private static final long REVIEW_ID = 17L;
    private static final Instant NOW = Instant.parse("2026-07-11T03:00:00Z");

    @Mock
    private AnswerReviewMapper answerReviewMapper;

    private AnswerReviewService answerReviewService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        answerReviewService = new AnswerReviewServiceImpl(answerReviewMapper, clock);
    }

    @Test
    void getReviewsReturnsGlobalSummaryAndFilteredItems() {
        when(answerReviewMapper.findSummary(USER_ID)).thenReturn(
                new AnswerReviewData.ReviewSummaryRow(4, 3, 1)
        );
        when(answerReviewMapper.findReviews(USER_ID, "OPEN")).thenReturn(
                List.of(reviewRow("PENDING", 0, null))
        );

        AnswerReviewService.ReviewListResponse result =
                answerReviewService.getReviews(USER_ID, "open");

        assertThat(result.totalCount()).isEqualTo(4);
        assertThat(result.openCount()).isEqualTo(3);
        assertThat(result.recoveredCount()).isEqualTo(1);
        assertThat(result.reviews()).hasSize(1);
        assertThat(result.reviews().getFirst().selectedOption().content())
                .isEqualTo("Wrong answer");
        assertThat(result.reviews().getFirst().correctOption().content())
                .isEqualTo("Correct answer");
        assertThat(result.reviews().getFirst().passageContent())
                .isEqualTo("Passage content");
    }

    @Test
    void updateStatusRecoversReviewAndIncrementsThroughMapper() {
        AnswerReviewData.ReviewRow pending = reviewRow("PENDING", 0, null);
        AnswerReviewData.ReviewRow recovered = reviewRow("RECOVERED", 1, NOW);
        when(answerReviewMapper.findReview(USER_ID, REVIEW_ID))
                .thenReturn(pending, recovered);

        AnswerReviewService.ReviewItem result = answerReviewService.updateStatus(
                USER_ID,
                REVIEW_ID,
                "recovered"
        );

        verify(answerReviewMapper).updateStatus(USER_ID, REVIEW_ID, "RECOVERED", NOW);
        assertThat(result.status()).isEqualTo("RECOVERED");
        assertThat(result.retryCount()).isEqualTo(1);
        assertThat(result.recoveredAt()).isEqualTo(NOW);
    }

    @Test
    void updateStatusRejectsUnsupportedStatusBeforeWriting() {
        assertThatThrownBy(() -> answerReviewService.updateStatus(
                USER_ID,
                REVIEW_ID,
                "REVIEWED"
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(exception.getCode()).isEqualTo("INVALID_REVIEW_STATUS");
        });

        verify(answerReviewMapper, never()).updateStatus(
                USER_ID,
                REVIEW_ID,
                "REVIEWED",
                NOW
        );
    }

    @Test
    void getReviewHidesReviewsOwnedByAnotherUser() {
        when(answerReviewMapper.findReview(USER_ID, REVIEW_ID)).thenReturn(null);

        assertThatThrownBy(() -> answerReviewService.getReview(USER_ID, REVIEW_ID))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("ANSWER_REVIEW_NOT_FOUND");
                });
    }

    private AnswerReviewData.ReviewRow reviewRow(
            String status,
            int retryCount,
            Instant recoveredAt
    ) {
        return new AnswerReviewData.ReviewRow(
                REVIEW_ID,
                101L,
                status,
                retryCount,
                LocalDate.of(2026, 7, 11),
                "Passage title",
                "Science",
                "Passage content",
                (short) 2,
                "Question content",
                1001L,
                (short) 2,
                "Wrong answer",
                1002L,
                (short) 3,
                "Correct answer",
                "Explanation",
                "Evidence",
                NOW.minusSeconds(3600),
                recoveredAt,
                recoveredAt
        );
    }
}

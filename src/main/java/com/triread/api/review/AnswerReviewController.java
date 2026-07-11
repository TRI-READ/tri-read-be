package com.triread.api.review;

import com.triread.api.auth.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class AnswerReviewController {

    private final AnswerReviewService answerReviewService;

    public AnswerReviewController(AnswerReviewService answerReviewService) {
        this.answerReviewService = answerReviewService;
    }

    @GetMapping
    public AnswerReviewService.ReviewListResponse reviews(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "OPEN") String status
    ) {
        return answerReviewService.getReviews(principal.userId(), status);
    }

    @GetMapping("/{reviewId}")
    public AnswerReviewService.ReviewItem review(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Positive @PathVariable long reviewId
    ) {
        return answerReviewService.getReview(principal.userId(), reviewId);
    }

    @PatchMapping("/{reviewId}")
    public AnswerReviewService.ReviewItem update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Positive @PathVariable long reviewId,
            @Valid @RequestBody UpdateReviewRequest request
    ) {
        return answerReviewService.updateStatus(
                principal.userId(),
                reviewId,
                request.status()
        );
    }

    public record UpdateReviewRequest(@NotBlank String status) {
    }
}

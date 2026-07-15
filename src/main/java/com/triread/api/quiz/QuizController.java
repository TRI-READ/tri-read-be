package com.triread.api.quiz;

import com.triread.api.auth.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping("/today")
    public QuizService.TodayQuizResponse today(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return quizService.getTodayQuiz(principal.userId());
    }

    @PostMapping("/{quizSetId}/attempts")
    public ResponseEntity<QuizService.QuizResultResponse> submit(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Positive @PathVariable long quizSetId,
            @Valid @RequestBody SubmitAttemptRequest request
    ) {
        List<QuizService.SubmittedAnswer> answers = request.answers().stream()
                .map(answer -> new QuizService.SubmittedAnswer(
                        answer.questionId(),
                        answer.selectedOptionId()
                ))
                .toList();

        QuizService.QuizResultResponse result =
                quizService.submitAttempt(principal.userId(), quizSetId, answers);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    public record SubmitAttemptRequest(
            @NotNull
            @Size(min = 3, max = 3)
            List<@Valid AnswerRequest> answers
    ) {
    }

    public record AnswerRequest(
            @Positive long questionId,
            @Positive long selectedOptionId
    ) {
    }
}

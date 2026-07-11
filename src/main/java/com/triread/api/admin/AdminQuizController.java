package com.triread.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/quizzes")
public class AdminQuizController {
    private final AdminQuizService service;
    public AdminQuizController(AdminQuizService service) { this.service = service; }

    @GetMapping public List<AdminQuizService.QuizSummary> list() { return service.getQuizzes(); }
    @GetMapping("/{quizSetId}") public AdminQuizService.QuizDetail detail(@Positive @PathVariable long quizSetId) { return service.getQuiz(quizSetId); }
    @PostMapping public ResponseEntity<AdminQuizService.QuizDetail> create(@Valid @RequestBody CreateQuizRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDraft(toCommand(request)));
    }
    @PutMapping("/{quizSetId}") public AdminQuizService.QuizDetail update(@Positive @PathVariable long quizSetId,
            @Valid @RequestBody CreateQuizRequest request) { return service.updateDraft(quizSetId, toCommand(request)); }
    @DeleteMapping("/{quizSetId}") public ResponseEntity<Void> delete(@Positive @PathVariable long quizSetId) {
        service.deleteDraft(quizSetId); return ResponseEntity.noContent().build();
    }
    @PostMapping("/{quizSetId}/publish") public AdminQuizService.QuizDetail publish(@Positive @PathVariable long quizSetId) { return service.publish(quizSetId); }

    private AdminQuizService.CreateQuiz toCommand(CreateQuizRequest request) {
        return new AdminQuizService.CreateQuiz(request.challengeDate(), request.passages().stream().map(p ->
                new AdminQuizService.CreatePassage(p.title(), p.topic(), p.content(), p.questions().stream().map(q ->
                        new AdminQuizService.CreateQuestion(q.content(), q.options(), q.correctOptionPosition(), q.explanation(), q.evidence())
                ).toList())).toList());
    }

    public record CreateQuizRequest(@NotNull LocalDate challengeDate, @NotNull @Size(min=3,max=3) List<@Valid PassageRequest> passages) {}
    public record PassageRequest(@Size(max=300) String title, @Size(max=100) String topic, @NotBlank String content,
                                 @NotNull @Size(min=3,max=3) List<@Valid QuestionRequest> questions) {}
    public record QuestionRequest(@NotBlank String content, @NotNull @Size(min=4,max=4) List<@NotBlank String> options,
                                  @Min(1) @Max(4) int correctOptionPosition, @NotBlank String explanation, String evidence) {}
}

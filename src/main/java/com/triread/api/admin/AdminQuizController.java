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
import java.util.Map;
import com.triread.api.audit.AdminAuditService;
import com.triread.api.auth.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/quizzes")
public class AdminQuizController {
    private final AdminQuizService service;
    private final AdminAuditService auditService;
    public AdminQuizController(AdminQuizService service, AdminAuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public AdminQuizService.QuizPage list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size
    ) {
        return service.getQuizzes(page, size);
    }
    @GetMapping("/{quizSetId}") public AdminQuizService.QuizDetail detail(@Positive @PathVariable long quizSetId) { return service.getQuiz(quizSetId); }
    @PostMapping public ResponseEntity<AdminQuizService.QuizDetail> create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody CreateQuizRequest request) {
        AdminQuizService.QuizDetail created = service.createDraft(toCommand(request));
        auditService.record(principal.userId(), "QUIZ_DRAFT_CREATED", "QUIZ_SET",
                created.quiz().quizSetId(), Map.of("challengeDate", request.challengeDate().toString()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    @PutMapping("/{quizSetId}") public AdminQuizService.QuizDetail update(
            @AuthenticationPrincipal AuthPrincipal principal, @Positive @PathVariable long quizSetId,
            @Valid @RequestBody CreateQuizRequest request) {
        AdminQuizService.QuizDetail updated = service.updateDraft(quizSetId, toCommand(request));
        auditService.record(principal.userId(), "QUIZ_DRAFT_UPDATED", "QUIZ_SET", quizSetId, Map.of());
        return updated;
    }
    @DeleteMapping("/{quizSetId}") public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal, @Positive @PathVariable long quizSetId) {
        service.deleteDraft(quizSetId);
        auditService.record(principal.userId(), "QUIZ_DRAFT_DELETED", "QUIZ_SET", quizSetId, Map.of());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{quizSetId}/publish") public AdminQuizService.QuizDetail publish(
            @AuthenticationPrincipal AuthPrincipal principal, @Positive @PathVariable long quizSetId) {
        AdminQuizService.QuizDetail published = service.publish(quizSetId);
        auditService.record(principal.userId(), "QUIZ_PUBLISHED", "QUIZ_SET", quizSetId, Map.of());
        return published;
    }

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

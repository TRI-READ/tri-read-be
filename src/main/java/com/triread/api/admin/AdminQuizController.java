package com.triread.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
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
            @RequestParam(defaultValue = "6") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate challengeDate,
            @RequestParam(required = false) String keyword
    ) {
        return service.getQuizzes(page, size, status, challengeDate, keyword);
    }

    @GetMapping("/{quizSetId}")
    public AdminQuizService.QuizDetail detail(@Positive @PathVariable long quizSetId) {
        return service.getQuiz(quizSetId);
    }

    @PostMapping
    public ResponseEntity<AdminQuizService.QuizDetail> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateQuizRequest request
    ) {
        AdminQuizService.QuizDetail created = service.createDraft(toCommand(request));
        auditService.record(principal.userId(), "QUIZ_DRAFT_CREATED", "QUIZ_SET",
                created.quiz().quizSetId(), Map.of("challengeDate", request.challengeDate().toString()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{quizSetId}")
    public AdminQuizService.QuizDetail update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Positive @PathVariable long quizSetId,
            @Valid @RequestBody CreateQuizRequest request
    ) {
        AdminQuizService.QuizDetail updated = service.updateDraft(quizSetId, toCommand(request));
        auditService.record(principal.userId(), "QUIZ_DRAFT_UPDATED", "QUIZ_SET", quizSetId, Map.of());
        return updated;
    }

    @DeleteMapping("/{quizSetId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Positive @PathVariable long quizSetId
    ) {
        service.deleteDraft(quizSetId);
        auditService.record(principal.userId(), "QUIZ_DRAFT_DELETED", "QUIZ_SET", quizSetId, Map.of());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{quizSetId}/publish")
    public AdminQuizService.QuizDetail publish(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Positive @PathVariable long quizSetId
    ) {
        AdminQuizService.QuizDetail published = service.publish(quizSetId);
        auditService.record(principal.userId(), "QUIZ_PUBLISHED", "QUIZ_SET", quizSetId, Map.of());
        return published;
    }

    @PostMapping("/{quizSetId}/review")
    public AdminQuizService.QuizDetail review(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Positive @PathVariable long quizSetId
    ) {
        AdminQuizService.QuizDetail reviewed = service.review(quizSetId);
        auditService.record(principal.userId(), "QUIZ_REVIEWED", "QUIZ_SET", quizSetId, Map.of());
        return reviewed;
    }

    @PostMapping("/bulk/publish")
    public AdminQuizService.BulkResult bulkPublish(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody BulkQuizRequest request
    ) {
        AdminQuizService.BulkResult result = service.publishAll(request.quizSetIds());
        auditService.record(principal.userId(), "QUIZZES_BULK_PUBLISHED", "QUIZ_SET", null,
                Map.of("quizSetIds", request.quizSetIds()));
        return result;
    }

    @PostMapping("/bulk/delete")
    public AdminQuizService.BulkResult bulkDelete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody BulkQuizRequest request
    ) {
        AdminQuizService.BulkResult result = service.deleteAll(request.quizSetIds());
        auditService.record(principal.userId(), "QUIZZES_BULK_DELETED", "QUIZ_SET", null,
                Map.of("quizSetIds", request.quizSetIds()));
        return result;
    }

    private AdminQuizService.CreateQuiz toCommand(CreateQuizRequest request) {
        List<AdminQuizService.CreatePassage> passages = new ArrayList<>();

        for (PassageRequest passage : request.passages()) {
            List<AdminQuizService.CreateQuestion> questions = new ArrayList<>();
            for (QuestionRequest question : passage.questions()) {
                questions.add(new AdminQuizService.CreateQuestion(
                        question.content(),
                        question.options(),
                        question.correctOptionPosition(),
                        question.explanation(),
                        question.evidence()
                ));
            }

            passages.add(new AdminQuizService.CreatePassage(
                    passage.title(),
                    passage.topic(),
                    passage.content(),
                    questions
            ));
        }

        return new AdminQuizService.CreateQuiz(request.challengeDate(), passages);
    }

    public record CreateQuizRequest(
            @NotNull LocalDate challengeDate,
            @NotNull @Size(min = 3, max = 3) List<@Valid PassageRequest> passages
    ) {
    }

    public record PassageRequest(
            @Size(max = 300) String title,
            @Size(max = 100) String topic,
            @NotBlank String content,
            @NotNull @Size(min = 3, max = 3) List<@Valid QuestionRequest> questions
    ) {
    }

    public record QuestionRequest(
            @NotBlank String content,
            @NotNull @Size(min = 4, max = 4) List<@NotBlank String> options,
            @Min(1) @Max(4) int correctOptionPosition,
            @NotBlank String explanation,
            String evidence
    ) {
    }

    public record BulkQuizRequest(
            @NotNull @Size(min = 1, max = 50) List<@Positive Long> quizSetIds
    ) {
    }
}

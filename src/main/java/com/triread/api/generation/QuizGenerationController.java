package com.triread.api.generation;

import jakarta.validation.Valid;
import com.triread.api.audit.AdminAuditService;
import com.triread.api.auth.AuthPrincipal;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/quiz-generations")
public class QuizGenerationController {
    private final QuizGenerationService service;
    private final AdminAuditService auditService;

    public QuizGenerationController(QuizGenerationService service, AdminAuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @PostMapping
    public ResponseEntity<QuizGenerationService.GenerationResult> generate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody GenerateQuizRequest request) {
        QuizGenerationService.GenerationResult result = service.generate(request.targetDate());
        auditService.record(principal.userId(), "QUIZ_GENERATION_REQUESTED", "GENERATION_LOG",
                result.generationLogId(), Map.of("targetDate", request.targetDate().toString(),
                        "status", result.status()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public QuizGenerationService.GenerationLogPage logs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate targetDate) {
        return service.getLogs(page, size, status, targetDate);
    }

    @GetMapping("/{generationLogId}")
    public QuizGenerationService.GenerationDetail log(
            @PathVariable @Positive long generationLogId) {
        return service.getLog(generationLogId);
    }

    @PostMapping("/{generationLogId}/retry")
    public ResponseEntity<QuizGenerationService.GenerationResult> retry(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive long generationLogId) {
        QuizGenerationService.GenerationResult result = service.retry(generationLogId);
        auditService.record(principal.userId(), "QUIZ_GENERATION_RETRIED", "GENERATION_LOG",
                generationLogId, Map.of("newGenerationLogId", result.generationLogId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    public record GenerateQuizRequest(@NotNull LocalDate targetDate) {}
}

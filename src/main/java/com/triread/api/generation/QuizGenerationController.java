package com.triread.api.generation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
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

    public QuizGenerationController(QuizGenerationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<QuizGenerationService.GenerationResult> generate(
            @Valid @RequestBody GenerateQuizRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.generate(request.targetDate()));
    }

    @GetMapping
    public List<QuizGenerationData.GenerationLogRow> logs() {
        return service.getLogs();
    }

    @GetMapping("/{generationLogId}")
    public QuizGenerationService.GenerationDetail log(
            @PathVariable @Positive long generationLogId) {
        return service.getLog(generationLogId);
    }

    @PostMapping("/{generationLogId}/retry")
    public ResponseEntity<QuizGenerationService.GenerationResult> retry(
            @PathVariable @Positive long generationLogId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.retry(generationLogId));
    }

    public record GenerateQuizRequest(@NotNull LocalDate targetDate) {}
}

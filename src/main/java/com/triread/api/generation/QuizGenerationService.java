package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;
import java.time.LocalDate;
import java.util.List;

public interface QuizGenerationService {
    GenerationResult generate(LocalDate targetDate);
    List<QuizGenerationData.GenerationLogRow> getLogs();
    GenerationDetail getLog(long generationLogId);

    record GenerationResult(long generationLogId, String status, int attemptCount,
                            int validationScore, boolean autoPublished,
                            AdminQuizService.QuizDetail quiz) {}

    record ValidationResult(long validationResultId, int attemptNumber, String validationType,
                            boolean passed, int score, List<QuizValidation.Issue> issues,
                            java.time.Instant createdAt) {}

    record GenerationDetail(QuizGenerationData.GenerationLogRow log,
                            List<ValidationResult> validations) {}
}

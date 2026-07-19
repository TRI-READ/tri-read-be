package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;
import com.triread.api.prompt.PromptTemplateService;
import java.time.LocalDate;
import java.util.List;

public interface QuizContentGenerator {
    AdminQuizService.CreateQuiz generate(
            LocalDate targetDate,
            List<QuizGenerationData.RecentPassageRow> recentPassages,
            PromptTemplateService.PromptSnapshot prompt
    );
}

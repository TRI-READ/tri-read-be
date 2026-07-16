package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;
import java.time.LocalDate;
import java.util.List;

public interface QuizContentGenerator {
    AdminQuizService.CreateQuiz generate(
            LocalDate targetDate,
            List<QuizGenerationData.RecentPassageRow> recentPassages
    );
}

package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;
import java.time.LocalDate;

public interface QuizContentGenerator {
    AdminQuizService.CreateQuiz generate(LocalDate targetDate);
}

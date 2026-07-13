package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;

public interface QuizContentValidator {
    QuizValidation.Result validate(AdminQuizService.CreateQuiz quiz);
}

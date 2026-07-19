package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;
import com.triread.api.prompt.PromptTemplateService;

public interface QuizAiGateway extends QuizContentGenerator {
    QuizValidation.Result validate(AdminQuizService.CreateQuiz quiz,
                                   PromptTemplateService.PromptSnapshot prompt);

    String provider();
    String generationModel();
    String validationModel();
}

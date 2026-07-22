package com.triread.api.generation;

import com.triread.api.prompt.PromptTemplateService;
import java.util.List;

public interface QuizAiGateway extends QuizContentGenerator {
    QuizGenerationData.GeneratedQuiz repair(QuizGenerationData.GeneratedQuiz quiz,
                                            List<QuizValidation.Issue> issues,
                                            PromptTemplateService.PromptSnapshot prompt);

    QuizValidation.Result validate(QuizGenerationData.GeneratedQuiz quiz,
                                   PromptTemplateService.PromptSnapshot prompt);

    String provider();
    String generationModel();
    String validationModel();
}

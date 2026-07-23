package com.triread.api.generation;

import com.triread.api.prompt.PromptTemplateService;
import java.util.List;

public interface QuizAiGateway extends QuizContentGenerator {
    QuizGenerationData.SourceDiscovery discoverSources(java.time.LocalDate targetDate);

    QuizGenerationData.GeneratedQuiz generate(QuizGenerationData.SourceBrief sourceBrief,
                                               List<QuizGenerationData.RecentPassageRow> recentPassages,
                                               PromptTemplateService.PromptSnapshot prompt);

    QuizGenerationData.GeneratedQuiz repair(QuizGenerationData.GeneratedQuiz quiz,
                                            List<QuizValidation.Issue> issues,
                                            PromptTemplateService.PromptSnapshot prompt,
                                            QuizGenerationData.SourceBrief sourceBrief);

    default QuizGenerationData.GeneratedQuiz repair(QuizGenerationData.GeneratedQuiz quiz,
                                                    List<QuizValidation.Issue> issues,
                                                    PromptTemplateService.PromptSnapshot prompt) {
        return repair(quiz, issues, prompt, new QuizGenerationData.SourceBrief(
                0, quiz.challengeDate(), "DISABLED", sourceModel(), "", null, List.of()));
    }

    QuizValidation.Result validate(QuizGenerationData.GeneratedQuiz quiz,
                                   PromptTemplateService.PromptSnapshot prompt);

    String provider();
    String generationModel();
    String validationModel();
    String sourceModel();
}

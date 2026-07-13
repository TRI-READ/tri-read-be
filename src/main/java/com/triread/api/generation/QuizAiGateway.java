package com.triread.api.generation;

public interface QuizAiGateway extends QuizContentGenerator, QuizContentValidator {
    String generationModel();
    String validationModel();
    String promptVersion();
}

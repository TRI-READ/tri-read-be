package com.triread.api.generation;

public interface QuizAiGateway extends QuizContentGenerator, QuizContentValidator {
    String provider();
    String generationModel();
    String validationModel();
    String promptVersion();
}

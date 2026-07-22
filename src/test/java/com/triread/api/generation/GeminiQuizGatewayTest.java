package com.triread.api.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triread.api.common.ApiException;
import com.triread.api.prompt.PromptTemplateService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GeminiQuizGatewayTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void marksProductionConstructorForSpringInjection() throws Exception {
        assertThat(GeminiQuizGateway.class
                .getConstructor(QuizGenerationProperties.class, ObjectMapper.class)
                .isAnnotationPresent(Autowired.class)).isTrue();
    }

    @Test
    void usesCurrentStableHighVolumeModelByDefault() {
        QuizGenerationProperties properties = new QuizGenerationProperties();

        assertThat(properties.getGemini().getGenerationModel()).isEqualTo("gemini-3.1-flash-lite");
        assertThat(properties.getGemini().getValidationModel()).isEqualTo("gemini-3.1-flash-lite");
        assertThat(properties.getGemini().getPromptVersion()).isEqualTo("v2");
    }

    @Test
    void extractsStructuredJsonFromCandidateText() throws Exception {
        GeminiQuizGateway gateway = gateway();
        JsonNode response = objectMapper.readTree("""
                {"candidates":[{"content":{"parts":[{"text":"{\\"score\\":97}"}]}}]}
                """);

        assertThat(gateway.extractOutputText(response).path("score").asInt()).isEqualTo(97);
    }

    @Test
    void rejectsResponseWithoutCandidateText() throws Exception {
        GeminiQuizGateway gateway = gateway();

        assertThatThrownBy(() -> gateway.extractOutputText(objectMapper.readTree("{}")))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("GEMINI_RESPONSE_EMPTY"));
    }

    @Test
    void rejectsGenerationWhenApiKeyIsMissing() {
        assertThatThrownBy(() -> gateway().generate(LocalDate.of(2026, 7, 13), List.of(), prompt()))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("GEMINI_API_KEY_MISSING"));
    }

    @Test
    void includesRecentPassagesAsExplicitTopicExclusions() {
        GeminiQuizGateway gateway = gateway();

        String input = gateway.generationInput(LocalDate.of(2026, 7, 16), List.of(
                new QuizGenerationData.RecentPassageRow(
                        LocalDate.of(2026, 7, 15), 2,
                        "Quantum computing and information processing", "Science", "")));

        assertThat(input)
                .contains("Changing only the title or angle still counts as reuse")
                .contains("Quantum computing and information processing")
                .contains("area 2");
    }

    @Test
    void selectsOnlyPassagesNamedByValidationIssuesForRepair() {
        GeminiQuizGateway gateway = gateway();

        Set<Integer> positions = gateway.repairPositions(List.of(
                new QuizValidation.Issue("ERROR", "AMBIGUOUS", 2, 1, "Ambiguous"),
                new QuizValidation.Issue("ERROR", "BAD_EVIDENCE", 2, 3, "Bad evidence")));

        assertThat(positions).containsExactly(2);
    }

    @Test
    void mergesRequestedRepairWithoutChangingOtherPassages() {
        GeminiQuizGateway gateway = gateway();
        QuizGenerationData.GeneratedQuiz quiz = RuleBasedQuizValidatorTest.validGeneratedQuiz();
        QuizGenerationData.GeneratedPassage original = quiz.passages().get(1);
        QuizGenerationData.GeneratedPassage replacement = new QuizGenerationData.GeneratedPassage(
                "replacement", original.topic(), original.content(), original.questions());

        QuizGenerationData.GeneratedQuiz merged = gateway.mergeRepairs(quiz, Set.of(2), List.of(
                new GeminiQuizGateway.PassageRepair(2, replacement)));

        assertThat(merged.passages().get(0)).isSameAs(quiz.passages().get(0));
        assertThat(merged.passages().get(1).title()).isEqualTo("replacement");
        assertThat(merged.passages().get(2)).isSameAs(quiz.passages().get(2));
    }

    @Test
    void rejectsRepairThatDoesNotMatchRequestedPositions() {
        GeminiQuizGateway gateway = gateway();
        QuizGenerationData.GeneratedQuiz quiz = RuleBasedQuizValidatorTest.validGeneratedQuiz();

        assertThatThrownBy(() -> gateway.mergeRepairs(quiz, Set.of(1, 2), List.of(
                new GeminiQuizGateway.PassageRepair(1, quiz.passages().getFirst()))))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("GEMINI_REPAIR_RESPONSE_INVALID"));
    }

    private GeminiQuizGateway gateway() {
        QuizGenerationProperties properties = new QuizGenerationProperties();
        return new GeminiQuizGateway(properties, objectMapper);
    }

    private PromptTemplateService.PromptSnapshot prompt() {
        return new PromptTemplateService.PromptSnapshot(1L, "GENERATION", 2, "instructions", "hash");
    }
}

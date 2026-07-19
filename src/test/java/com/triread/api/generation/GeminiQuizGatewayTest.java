package com.triread.api.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triread.api.common.ApiException;
import com.triread.api.prompt.PromptTemplateService;
import java.time.LocalDate;
import java.util.List;
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

    private GeminiQuizGateway gateway() {
        QuizGenerationProperties properties = new QuizGenerationProperties();
        return new GeminiQuizGateway(properties, objectMapper);
    }

    private PromptTemplateService.PromptSnapshot prompt() {
        return new PromptTemplateService.PromptSnapshot(1L, "GENERATION", 2, "instructions", "hash");
    }
}

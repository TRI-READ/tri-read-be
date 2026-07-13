package com.triread.api.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triread.api.common.ApiException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GeminiQuizGatewayTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        assertThatThrownBy(() -> gateway().generate(LocalDate.of(2026, 7, 13)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("GEMINI_API_KEY_MISSING"));
    }

    private GeminiQuizGateway gateway() {
        QuizGenerationProperties properties = new QuizGenerationProperties();
        return new GeminiQuizGateway(properties, objectMapper);
    }
}

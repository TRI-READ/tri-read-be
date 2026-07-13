package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;
import com.triread.api.common.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "app.quiz-generation", name = "provider", havingValue = "gemini")
public class GeminiQuizGateway implements QuizAiGateway {
    private static final String GENERATION_INSTRUCTIONS = """
            You create original Korean non-fiction reading quizzes for Korean high-school seniors.
            Produce exactly 3 passages with distinct topics: humanities/social science, science/technology,
            and economics/law/interdisciplinary. Each passage must have exactly 3 questions and each
            question exactly 4 unique options. Use only information stated or logically derivable from
            the passage. Ensure exactly one correct answer. Evidence must be an exact excerpt copied from
            the passage. Write original passages without copying or closely imitating published material.
            Vary question types among comprehension, inference, application, and argument structure.
            """;
    private static final String VALIDATION_INSTRUCTIONS = """
            You are an independent quality verifier for a Korean high-school senior reading quiz.
            Verify every answer using only the supplied passage. Reject ambiguous questions, multiple
            plausible answers, unsupported explanations, evidence that does not prove the answer,
            internal factual or logical contradictions, and content below the requested difficulty.
            Return a strict score from 0 to 100. passed may be true only when there are no ERROR issues
            and the score is at least 90. Do not trust the provided answer key without checking it.
            """;

    private final QuizGenerationProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiQuizGateway(QuizGenerationProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper,
                RestClient.builder().baseUrl(properties.getGemini().getBaseUrl()).build());
    }

    GeminiQuizGateway(QuizGenerationProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public AdminQuizService.CreateQuiz generate(LocalDate targetDate) {
        String input = "Create the TRI:READ quiz for " + targetDate
                + ". All passages and questions must be written in Korean.";
        JsonNode payload = call(generationModel(), GENERATION_INSTRUCTIONS, input,
                generationSchema(), 20_000);
        try {
            GeneratedQuiz generated = objectMapper.treeToValue(payload, GeneratedQuiz.class);
            return new AdminQuizService.CreateQuiz(targetDate, generated.passages().stream().map(passage ->
                    new AdminQuizService.CreatePassage(passage.title(), passage.topic(), passage.content(),
                            passage.questions().stream().map(question -> new AdminQuizService.CreateQuestion(
                                    question.content(), question.options(), question.correctOptionPosition(),
                                    question.explanation(), question.evidence())).toList())).toList());
        } catch (JacksonException exception) {
            throw gatewayError("GEMINI_GENERATION_RESPONSE_INVALID",
                    "Generated quiz JSON could not be parsed.", exception);
        }
    }

    @Override
    public QuizValidation.Result validate(AdminQuizService.CreateQuiz quiz) {
        try {
            String input = "Independently validate this quiz:\n" + objectMapper.writeValueAsString(quiz);
            JsonNode payload = call(validationModel(), VALIDATION_INSTRUCTIONS, input,
                    validationSchema(), 8_000);
            return objectMapper.treeToValue(payload, QuizValidation.Result.class);
        } catch (JacksonException exception) {
            throw gatewayError("GEMINI_VALIDATION_RESPONSE_INVALID",
                    "Validation JSON could not be parsed.", exception);
        }
    }

    private JsonNode call(String model, String instructions, String input,
                          JsonNode schema, int maxOutputTokens) {
        requireApiKey();
        Map<String, Object> request = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", instructions + "\n\n" + input)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseJsonSchema", schema,
                        "maxOutputTokens", maxOutputTokens)
        );
        try {
            JsonNode response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", properties.getGemini().getApiKey())
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            return extractOutputText(response);
        } catch (RestClientException exception) {
            throw gatewayError("GEMINI_REQUEST_FAILED", "Gemini request failed.", exception);
        }
    }

    JsonNode extractOutputText(JsonNode response) {
        if (response != null) {
            for (JsonNode candidate : response.path("candidates")) {
                for (JsonNode part : candidate.path("content").path("parts")) {
                    if (part.hasNonNull("text")) {
                        try {
                            return objectMapper.readTree(part.get("text").asText());
                        } catch (JacksonException exception) {
                            throw gatewayError("GEMINI_RESPONSE_INVALID",
                                    "Gemini returned invalid structured output.", exception);
                        }
                    }
                }
            }
        }
        throw gatewayError("GEMINI_RESPONSE_EMPTY", "Gemini returned no structured output.", null);
    }

    private void requireApiKey() {
        if (properties.getGemini().getApiKey() == null || properties.getGemini().getApiKey().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_API_KEY_MISSING",
                    "GEMINI_API_KEY must be configured before generating quizzes.");
        }
    }

    private ApiException gatewayError(String code, String message, Exception cause) {
        return new ApiException(HttpStatus.BAD_GATEWAY, code,
                cause == null ? message : message + " " + cause.getMessage());
    }

    private JsonNode generationSchema() {
        return readSchema("""
                {"type":"object","additionalProperties":false,"required":["passages"],"properties":{"passages":{"type":"array","minItems":3,"maxItems":3,"items":{"type":"object","additionalProperties":false,"required":["title","topic","content","questions"],"properties":{"title":{"type":"string"},"topic":{"type":"string"},"content":{"type":"string"},"questions":{"type":"array","minItems":3,"maxItems":3,"items":{"type":"object","additionalProperties":false,"required":["content","options","correctOptionPosition","explanation","evidence"],"properties":{"content":{"type":"string"},"options":{"type":"array","minItems":4,"maxItems":4,"items":{"type":"string"}},"correctOptionPosition":{"type":"integer","minimum":1,"maximum":4},"explanation":{"type":"string"},"evidence":{"type":"string"}}}}}}}}}
                """);
    }

    private JsonNode validationSchema() {
        return readSchema("""
                {"type":"object","additionalProperties":false,"required":["passed","score","issues"],"properties":{"passed":{"type":"boolean"},"score":{"type":"integer","minimum":0,"maximum":100},"issues":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["severity","code","passagePosition","questionPosition","message"],"properties":{"severity":{"type":"string","enum":["ERROR","WARNING"]},"code":{"type":"string"},"passagePosition":{"type":["integer","null"],"minimum":1,"maximum":3},"questionPosition":{"type":["integer","null"],"minimum":1,"maximum":3},"message":{"type":"string"}}}}}}
                """);
    }

    private JsonNode readSchema(String schema) {
        try {
            return objectMapper.readTree(schema);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid embedded JSON schema", exception);
        }
    }

    @Override public String provider() { return "GEMINI"; }
    @Override public String generationModel() { return properties.getGemini().getGenerationModel(); }
    @Override public String validationModel() { return properties.getGemini().getValidationModel(); }
    @Override public String promptVersion() { return properties.getGemini().getPromptVersion(); }

    public record GeneratedQuiz(List<GeneratedPassage> passages) {}
    public record GeneratedPassage(String title, String topic, String content,
                                   List<GeneratedQuestion> questions) {}
    public record GeneratedQuestion(String content, List<String> options,
                                    int correctOptionPosition, String explanation, String evidence) {}
}

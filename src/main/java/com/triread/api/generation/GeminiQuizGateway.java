package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;
import com.triread.api.common.ApiException;
import com.triread.api.prompt.PromptTemplateService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GeminiQuizGateway implements QuizAiGateway {
    private final QuizGenerationProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
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
    public AdminQuizService.CreateQuiz generate(
            LocalDate targetDate,
            List<QuizGenerationData.RecentPassageRow> recentPassages,
            PromptTemplateService.PromptSnapshot prompt
    ) {
        String input = generationInput(targetDate, recentPassages);
        JsonNode payload = call(generationModel(), prompt.content(), input,
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

    String generationInput(LocalDate targetDate,
                           List<QuizGenerationData.RecentPassageRow> recentPassages) {
        StringBuilder input = new StringBuilder()
                .append("Create the TRI:READ quiz for ").append(targetDate)
                .append(". All passages and questions must be written in Korean.\n")
                .append("Topic diversity is mandatory. Do not reuse the same core subject, entity, ")
                .append("theory, technology, event, or policy from the recent passages below. ")
                .append("Changing only the title or angle still counts as reuse.\n");
        if (recentPassages == null || recentPassages.isEmpty()) {
            return input.append("There are no recent passages to exclude.").toString();
        }
        input.append("Recent passages to exclude:\n");
        recentPassages.forEach(passage -> input
                .append("- area ").append(passage.position())
                .append(", ").append(passage.challengeDate())
                .append(": title=").append(display(passage.title()))
                .append(", topic=").append(display(passage.topic()))
                .append('\n'));
        return input.toString();
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "(unspecified)" : value.trim();
    }

    @Override
    public QuizValidation.Result validate(AdminQuizService.CreateQuiz quiz,
                                          PromptTemplateService.PromptSnapshot prompt) {
        try {
            String input = "Independently validate this quiz:\n" + objectMapper.writeValueAsString(quiz);
            JsonNode payload = call(validationModel(), prompt.content(), input,
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
        } catch (RestClientResponseException exception) {
            String code = switch (exception.getStatusCode().value()) {
                case 429 -> "GEMINI_RATE_LIMITED";
                case 502, 503, 504 -> "GEMINI_UNAVAILABLE";
                default -> "GEMINI_REQUEST_FAILED";
            };
            throw gatewayError(code, "Gemini request failed.", exception);
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

    public record GeneratedQuiz(List<GeneratedPassage> passages) {}
    public record GeneratedPassage(String title, String topic, String content,
                                   List<GeneratedQuestion> questions) {}
    public record GeneratedQuestion(String content, List<String> options,
                                    int correctOptionPosition, String explanation, String evidence) {}
}

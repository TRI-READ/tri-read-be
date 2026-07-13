package com.triread.api.generation;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.triread.api.admin.AdminQuizService;
import com.triread.api.common.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OpenAiQuizGateway implements QuizAiGateway {
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

    public OpenAiQuizGateway(QuizGenerationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(properties.getOpenai().getBaseUrl()).build();
    }

    @Override
    public AdminQuizService.CreateQuiz generate(LocalDate targetDate) {
        String input = "Create the TRI:READ quiz for " + targetDate
                + ". All passages and questions must be written in Korean.";
        JsonNode payload = call(generationModel(), GENERATION_INSTRUCTIONS, input,
                "tri_read_quiz", generationSchema(), 20_000);
        try {
            GeneratedQuiz generated = objectMapper.treeToValue(payload, GeneratedQuiz.class);
            return new AdminQuizService.CreateQuiz(targetDate, generated.passages().stream().map(passage ->
                    new AdminQuizService.CreatePassage(passage.title(), passage.topic(), passage.content(),
                            passage.questions().stream().map(question -> new AdminQuizService.CreateQuestion(
                                    question.content(), question.options(), question.correctOptionPosition(),
                                    question.explanation(), question.evidence())).toList())).toList());
        } catch (JacksonException exception) {
            throw gatewayError("OPENAI_GENERATION_RESPONSE_INVALID", "Generated quiz JSON could not be parsed.", exception);
        }
    }

    @Override
    public QuizValidation.Result validate(AdminQuizService.CreateQuiz quiz) {
        try {
            String input = "Independently validate this quiz:\n" + objectMapper.writeValueAsString(quiz);
            JsonNode payload = call(validationModel(), VALIDATION_INSTRUCTIONS, input,
                    "tri_read_validation", validationSchema(), 8_000);
            return objectMapper.treeToValue(payload, QuizValidation.Result.class);
        } catch (JacksonException exception) {
            throw gatewayError("OPENAI_VALIDATION_RESPONSE_INVALID", "Validation JSON could not be parsed.", exception);
        }
    }

    private JsonNode call(String model, String instructions, String input, String schemaName,
                          JsonNode schema, int maxOutputTokens) {
        requireApiKey();
        Map<String, Object> request = Map.of(
                "model", model,
                "instructions", instructions,
                "input", input,
                "max_output_tokens", maxOutputTokens,
                "text", Map.of("format", Map.of(
                        "type", "json_schema", "name", schemaName, "strict", true, "schema", schema
                ))
        );
        try {
            JsonNode response = restClient.post()
                    .uri("/v1/responses")
                    .header("Authorization", "Bearer " + properties.getOpenai().getApiKey())
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            return extractOutputText(response);
        } catch (RestClientException exception) {
            throw gatewayError("OPENAI_REQUEST_FAILED", "OpenAI request failed.", exception);
        }
    }

    private JsonNode extractOutputText(JsonNode response) {
        if (response != null) {
            for (JsonNode output : response.path("output")) {
                for (JsonNode content : output.path("content")) {
                    if ("output_text".equals(content.path("type").asText()) && content.hasNonNull("text")) {
                        try {
                            return objectMapper.readTree(content.get("text").asText());
                        } catch (JacksonException exception) {
                            throw gatewayError("OPENAI_RESPONSE_INVALID", "OpenAI returned invalid structured output.", exception);
                        }
                    }
                }
            }
        }
        throw gatewayError("OPENAI_RESPONSE_EMPTY", "OpenAI returned no structured output.", null);
    }

    private void requireApiKey() {
        if (properties.getOpenai().getApiKey() == null || properties.getOpenai().getApiKey().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OPENAI_API_KEY_MISSING",
                    "OPENAI_API_KEY must be configured before generating quizzes.");
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

    @Override public String generationModel() { return properties.getOpenai().getGenerationModel(); }
    @Override public String validationModel() { return properties.getOpenai().getValidationModel(); }
    @Override public String promptVersion() { return properties.getOpenai().getPromptVersion(); }

    public record GeneratedQuiz(List<GeneratedPassage> passages) {}
    public record GeneratedPassage(String title, String topic, String content,
                                   List<GeneratedQuestion> questions) {}
    public record GeneratedQuestion(String content, List<String> options,
                                    int correctOptionPosition, String explanation, String evidence) {}
}

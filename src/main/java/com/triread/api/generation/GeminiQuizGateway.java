package com.triread.api.generation;

import com.triread.api.common.ApiException;
import com.triread.api.prompt.PromptTemplateService;
import java.time.LocalDate;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
    public QuizGenerationData.GeneratedQuiz generate(
            LocalDate targetDate,
            List<QuizGenerationData.RecentPassageRow> recentPassages,
            PromptTemplateService.PromptSnapshot prompt
    ) {
        return generate(new QuizGenerationData.SourceBrief(0, targetDate, "DISABLED",
                sourceModel(), "", null, List.of()), recentPassages, prompt);
    }

    @Override
    public QuizGenerationData.GeneratedQuiz generate(
            QuizGenerationData.SourceBrief sourceBrief,
            List<QuizGenerationData.RecentPassageRow> recentPassages,
            PromptTemplateService.PromptSnapshot prompt
    ) {
        String input = generationInput(sourceBrief.targetDate(), recentPassages)
                + sourceBriefInput(sourceBrief);
        JsonNode payload = call(generationModel(), prompt.content(), input,
                generationSchema(), 20_000);
        try {
            GeneratedQuizPayload generated = objectMapper.treeToValue(payload, GeneratedQuizPayload.class);
            return new QuizGenerationData.GeneratedQuiz(
                    sourceBrief.targetDate(), generated.passages());
        } catch (JacksonException exception) {
            throw gatewayError("GEMINI_GENERATION_RESPONSE_INVALID",
                    "Generated quiz JSON could not be parsed.", exception);
        }
    }

    @Override
    public QuizGenerationData.GeneratedQuiz repair(
            QuizGenerationData.GeneratedQuiz quiz,
            List<QuizValidation.Issue> issues,
            PromptTemplateService.PromptSnapshot prompt,
            QuizGenerationData.SourceBrief sourceBrief
    ) {
        Set<Integer> positions = repairPositions(issues);
        try {
            String input = "Repair only the requested passages in this generated quiz.\n"
                    + "Requested passage positions: " + positions + "\n"
                    + "Validation issues: " + objectMapper.writeValueAsString(issues) + "\n"
                    + "Existing generated quiz: " + objectMapper.writeValueAsString(quiz) + "\n"
                    + sourceBriefInput(sourceBrief)
                    + "Return exactly one repair for every requested position. Do not return or rewrite "
                    + "unrequested passages. The replacement passage must satisfy the full generation prompt.";
            JsonNode payload = call(generationModel(), prompt.content(), input,
                    repairSchema(), Math.max(8_000, positions.size() * 6_000));
            RepairPayload repair = objectMapper.treeToValue(payload, RepairPayload.class);
            return mergeRepairs(quiz, positions, repair.repairs());
        } catch (JacksonException exception) {
            throw gatewayError("GEMINI_REPAIR_RESPONSE_INVALID",
                    "Repaired quiz JSON could not be parsed.", exception);
        }
    }

    @Override
    public QuizGenerationData.SourceDiscovery discoverSources(LocalDate targetDate) {
        requireApiKey();
        String input = """
                Find recent, factual news or public-institution material suitable for three
                independent Korean high-school nonfiction passages. Use a different domain for
                each area: science/technology, society/economy, and humanities/culture.
                Prefer reporting and primary institutions over blogs. For each area, synthesize
                facts supported by at least two independent sources. Do not copy article prose.
                Return plain text with exactly these markers:
                [AREA1] science/technology briefing
                [AREA2] society/economy briefing
                [AREA3] humanities/culture briefing
                Target quiz date: %s
                """.formatted(targetDate);
        Map<String, Object> request = Map.of(
                "contents", List.of(Map.of("role", "user",
                        "parts", List.of(Map.of("text", input)))),
                "tools", List.of(Map.of("google_search", Map.of())),
                "generationConfig", Map.of("maxOutputTokens", 4_000)
        );
        try {
            JsonNode response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", sourceModel())
                    .header("x-goog-api-key", properties.getGemini().getApiKey())
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            return parseGroundedSources(response);
        } catch (RestClientResponseException exception) {
            String code = exception.getStatusCode().value() == 429
                    ? "GEMINI_RATE_LIMITED" : "GEMINI_SOURCE_REQUEST_FAILED";
            throw gatewayError(code, "Gemini source discovery failed.", exception);
        } catch (RestClientException exception) {
            throw gatewayError("GEMINI_SOURCE_REQUEST_FAILED",
                    "Gemini source discovery failed.", exception);
        }
    }

    QuizGenerationData.SourceDiscovery parseGroundedSources(JsonNode response) {
        JsonNode candidate = response == null ? null : response.path("candidates").path(0);
        if (candidate == null || candidate.isMissingNode()) {
            throw gatewayError("GEMINI_SOURCE_RESPONSE_EMPTY",
                    "Gemini returned no grounded source briefing.", null);
        }
        StringBuilder briefing = new StringBuilder();
        candidate.path("content").path("parts").forEach(part -> {
            if (part.hasNonNull("text")) briefing.append(part.get("text").asText());
        });
        String text = briefing.toString();
        int area2 = text.indexOf("[AREA2]");
        int area3 = text.indexOf("[AREA3]");
        if (!text.contains("[AREA1]") || area2 < 0 || area3 < area2) {
            throw gatewayError("GEMINI_SOURCE_RESPONSE_INVALID",
                    "Grounded source briefing did not contain all three areas.", null);
        }

        JsonNode metadata = candidate.path("groundingMetadata");
        JsonNode chunks = metadata.path("groundingChunks");
        Map<String, QuizGenerationData.DiscoveredSource> unique = new LinkedHashMap<>();
        for (JsonNode support : metadata.path("groundingSupports")) {
            int endIndex = support.path("segment").path("endIndex").asInt(0);
            int position = endIndex <= area2 ? 1 : endIndex <= area3 ? 2 : 3;
            for (JsonNode index : support.path("groundingChunkIndices")) {
                JsonNode web = chunks.path(index.asInt()).path("web");
                String url = web.path("uri").asText("");
                String title = web.path("title").asText("");
                if (url.isBlank() || title.isBlank()) continue;
                String key = position + "|" + url;
                unique.putIfAbsent(key, new QuizGenerationData.DiscoveredSource(
                        position, title, publisher(url), null, url,
                        areaSummary(text, position, area2, area3)));
            }
        }
        List<QuizGenerationData.DiscoveredSource> sources = new ArrayList<>(unique.values());
        boolean complete = java.util.stream.IntStream.rangeClosed(1, 3)
                .allMatch(position -> sources.stream()
                        .filter(source -> source.passagePosition() == position)
                        .map(QuizGenerationData.DiscoveredSource::sourceUrl)
                        .distinct().count() >= 2);
        if (!complete) {
            throw gatewayError("GEMINI_SOURCE_GROUNDING_INSUFFICIENT",
                    "Each passage area requires at least two grounded sources.", null);
        }
        return new QuizGenerationData.SourceDiscovery(text, sources);
    }

    private String publisher(String rawUrl) {
        try {
            return URI.create(rawUrl).getHost();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String areaSummary(String text, int position, int area2, int area3) {
        int start = switch (position) {
            case 1 -> text.indexOf("[AREA1]") + 7;
            case 2 -> area2 + 7;
            default -> area3 + 7;
        };
        int end = switch (position) {
            case 1 -> area2;
            case 2 -> area3;
            default -> text.length();
        };
        return text.substring(Math.max(0, start), Math.max(start, end)).trim();
    }

    private String sourceBriefInput(QuizGenerationData.SourceBrief sourceBrief) {
        if (sourceBrief == null || !sourceBrief.grounded()) {
            return "\nNo verified source briefing is available. Do not claim current news facts.\n";
        }
        StringBuilder value = new StringBuilder("""

                Grounding rules:
                - Use AREA1 for passage 1, AREA2 for passage 2, AREA3 for passage 3.
                - Write an original explanatory passage; never copy source wording.
                - Do not invent facts beyond the briefing and cited sources.
                - Do not print URLs or citations inside the passage.
                Verified source briefing:
                """).append(sourceBrief.briefingText()).append("\nVerified references:\n");
        sourceBrief.sources().forEach(source -> value.append("- AREA")
                .append(source.passagePosition()).append(": ")
                .append(source.title()).append(" | ").append(source.sourceUrl()).append('\n'));
        return value.toString();
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
    public QuizValidation.Result validate(QuizGenerationData.GeneratedQuiz quiz,
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

    Set<Integer> repairPositions(List<QuizValidation.Issue> issues) {
        Set<Integer> positions = new TreeSet<>();
        if (issues != null) {
            issues.stream()
                    .map(QuizValidation.Issue::passagePosition)
                    .filter(position -> position != null && position >= 1 && position <= 3)
                    .forEach(positions::add);
        }
        return positions.isEmpty() ? Set.of(1, 2, 3) : positions;
    }

    QuizGenerationData.GeneratedQuiz mergeRepairs(
            QuizGenerationData.GeneratedQuiz quiz,
            Set<Integer> requestedPositions,
            List<PassageRepair> repairs
    ) {
        if (quiz == null || quiz.passages().size() != 3 || repairs == null) {
            throw gatewayError("GEMINI_REPAIR_RESPONSE_INVALID",
                    "Repair response does not match the existing quiz.", null);
        }
        Set<Integer> returnedPositions = new TreeSet<>();
        for (PassageRepair repair : repairs) {
            if (repair == null || repair.passagePosition() < 1 || repair.passagePosition() > 3
                    || repair.passage() == null || !returnedPositions.add(repair.passagePosition())) {
                throw gatewayError("GEMINI_REPAIR_RESPONSE_INVALID",
                        "Repair response contains an invalid or duplicate passage position.", null);
            }
        }
        if (!returnedPositions.equals(new TreeSet<>(requestedPositions))) {
            throw gatewayError("GEMINI_REPAIR_RESPONSE_INVALID",
                    "Repair response did not contain exactly the requested passages.", null);
        }
        List<QuizGenerationData.GeneratedPassage> merged = new ArrayList<>(quiz.passages());
        repairs.forEach(repair -> merged.set(repair.passagePosition() - 1, repair.passage()));
        return new QuizGenerationData.GeneratedQuiz(quiz.challengeDate(), merged);
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
                {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["passages"],
                  "properties": {
                    "passages": {
                      "type": "array",
                      "minItems": 3,
                      "maxItems": 3,
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["title", "topic", "content", "questions"],
                        "properties": {
                          "title": {"type": "string"},
                          "topic": {"type": "string"},
                          "content": {"type": "string"},
                          "questions": {
                            "type": "array",
                            "minItems": 3,
                            "maxItems": 3,
                            "items": {
                              "type": "object",
                              "additionalProperties": false,
                              "required": [
                                "content", "options", "correctOptionPosition", "explanation",
                                "evidence", "questionType", "optionRationales"
                              ],
                              "properties": {
                                "content": {"type": "string"},
                                "options": {
                                  "type": "array",
                                  "minItems": 4,
                                  "maxItems": 4,
                                  "items": {"type": "string"}
                                },
                                "correctOptionPosition": {
                                  "type": "integer",
                                  "minimum": 1,
                                  "maximum": 4
                                },
                                "explanation": {"type": "string"},
                                "evidence": {"type": "string"},
                                "questionType": {
                                  "type": "string",
                                  "enum": [
                                    "COMPREHENSION", "INFERENCE", "APPLICATION", "ARGUMENT_STRUCTURE"
                                  ]
                                },
                                "optionRationales": {
                                  "type": "array",
                                  "minItems": 4,
                                  "maxItems": 4,
                                  "items": {"type": "string"}
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """);
    }

    private JsonNode repairSchema() {
        return readSchema("""
                {"type":"object","additionalProperties":false,"required":["repairs"],"properties":{"repairs":{"type":"array","minItems":1,"maxItems":3,"items":{"type":"object","additionalProperties":false,"required":["passagePosition","passage"],"properties":{"passagePosition":{"type":"integer","minimum":1,"maximum":3},"passage":{"type":"object","additionalProperties":false,"required":["title","topic","content","questions"],"properties":{"title":{"type":"string"},"topic":{"type":"string"},"content":{"type":"string"},"questions":{"type":"array","minItems":3,"maxItems":3,"items":{"type":"object","additionalProperties":false,"required":["content","options","correctOptionPosition","explanation","evidence","questionType","optionRationales"],"properties":{"content":{"type":"string"},"options":{"type":"array","minItems":4,"maxItems":4,"items":{"type":"string"}},"correctOptionPosition":{"type":"integer","minimum":1,"maximum":4},"explanation":{"type":"string"},"evidence":{"type":"string"},"questionType":{"type":"string","enum":["COMPREHENSION","INFERENCE","APPLICATION","ARGUMENT_STRUCTURE"]},"optionRationales":{"type":"array","minItems":4,"maxItems":4,"items":{"type":"string"}}}}}}}}}}}}
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
    @Override public String sourceModel() { return properties.getGemini().getSourceModel(); }

    public record GeneratedQuizPayload(List<QuizGenerationData.GeneratedPassage> passages) {}
    public record RepairPayload(List<PassageRepair> repairs) {}
    public record PassageRepair(int passagePosition, QuizGenerationData.GeneratedPassage passage) {}
}

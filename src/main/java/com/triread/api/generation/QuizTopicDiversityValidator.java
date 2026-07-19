package com.triread.api.generation;

import com.triread.api.admin.AdminQuizService;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class QuizTopicDiversityValidator {
    private static final Set<String> GENERIC_TERMS = Set.of(
            "\uAC1C\uB150", "\uACFC\uC815", "\uAD00\uACC4", "\uAD6C\uC131", "\uAD6C\uC870",
            "\uBB38\uC81C", "\uBCC0\uD654", "\uBD84\uC11D", "\uC0AC\uD68C", "\uC5ED\uD560",
            "\uC601\uD5A5", "\uC6D0\uB9AC", "\uC758\uBBF8", "\uC774\uB860", "\uC774\uD574",
            "\uC815\uBCF4", "\uC804\uD658", "\uD604\uB300", "\uD604\uC0C1"
    );
    private static final List<String> PARTICLES = List.of(
            "\uC73C\uB85C", "\uC5D0\uC11C", "\uC5D0\uAC8C", "\uAE4C\uC9C0",
            "\uBD80\uD130", "\uCC98\uB7FC", "\uBCF4\uB2E4", "\uACFC", "\uC640",
            "\uC740", "\uB294", "\uC744", "\uB97C", "\uC774", "\uAC00", "\uC758", "\uB85C"
    );
    private static final Set<String> BROAD_AREA_TOPICS = Set.of(
            "\uC778\uBB38", "\uC778\uBB38\uD559", "\uC0AC\uD68C", "\uC0AC\uD68C\uACFC\uD559",
            "\uC778\uBB38 \uC0AC\uD68C", "\uC778\uBB38\uD559 \uC0AC\uD68C\uACFC\uD559",
            "\uACFC\uD559", "\uAE30\uC220", "\uACFC\uD559\uAE30\uC220", "\uACFC\uD559 \uAE30\uC220",
            "\uACBD\uC81C", "\uACBD\uC81C\uD559", "\uBC95", "\uBC95\uD559",
            "\uACBD\uC81C \uBC95", "\uBC95\uACFC \uACBD\uC81C", "\uBC95\uACFC \uACBD\uC81C\uD559",
            "\uACBD\uC81C\uD559 \uBC95\uD559", "\uACBD\uC81C \uBC95 \uC735\uD569",
            "\uACBD\uC81C\uD559 \uBC95\uD559 \uC735\uD569", "\uC735\uD569", "\uD559\uC81C\uAC04",
            "humanities", "social", "humanities social",
            "science", "technology", "science technology",
            "economics", "law", "economics law"
    );
    private static final Set<String> BROAD_AREA_TERMS = Set.of(
            "\uC778\uBB38", "\uC778\uBB38\uD559", "\uC0AC\uD68C", "\uC0AC\uD68C\uACFC\uD559",
            "\uACFC\uD559", "\uAE30\uC220", "\uACFC\uD559\uAE30\uC220", "\uACBD\uC81C", "\uACBD\uC81C\uD559",
            "\uBC95", "\uBC95\uD559", "\uC81C\uB3C4", "\uC735\uD569", "\uD559\uC81C\uAC04",
            "humanities", "social", "science", "technology", "economics", "law",
            "institution", "institutions", "interdisciplinary"
    );

    public QuizValidation.Result validate(
            AdminQuizService.CreateQuiz quiz,
            List<QuizGenerationData.RecentPassageRow> recentPassages
    ) {
        if (quiz == null || quiz.passages() == null || recentPassages == null
                || recentPassages.isEmpty()) {
            return new QuizValidation.Result(true, 100, List.of());
        }

        Set<Integer> duplicatedPositions = new HashSet<>();
        List<QuizValidation.Issue> issues = new java.util.ArrayList<>();
        for (int index = 0; index < quiz.passages().size(); index++) {
            int position = index + 1;
            AdminQuizService.CreatePassage passage = quiz.passages().get(index);
            recentPassages.stream()
                    .filter(recent -> recent.position() == position)
                    .filter(recent -> overlapsRecentPassage(passage, recent))
                    .findFirst()
                    .ifPresent(recent -> {
                        if (duplicatedPositions.add(position)) {
                            issues.add(new QuizValidation.Issue(
                                    "ERROR", "RECENT_TOPIC_OVERLAP", position, null,
                                    "Generated passage '" + passage.title() + "' (" + passage.topic()
                                            + ") overlaps recent passage '" + recent.title()
                                            + "' (" + recent.topic() + ")."
                            ));
                        }
                    });
        }
        int score = Math.max(0, 100 - issues.size() * 30);
        return new QuizValidation.Result(issues.isEmpty(), score, issues);
    }

    private boolean overlapsRecentPassage(
            AdminQuizService.CreatePassage passage,
            QuizGenerationData.RecentPassageRow recent
    ) {
        if (contentSimilarity(passage.content(), recent.content()) >= 0.68) return true;
        if (similarTitle(passage.title(), recent.title())) return true;

        boolean generatedTopicSpecific = isSpecificTopic(passage.topic());
        boolean recentTopicSpecific = isSpecificTopic(recent.topic());
        if (generatedTopicSpecific && recentTopicSpecific
                && similarTitle(passage.topic(), recent.topic())) return true;
        if (generatedTopicSpecific && similarTitle(passage.topic(), recent.title())) return true;
        return recentTopicSpecific && similarTitle(passage.title(), recent.topic());
    }

    double contentSimilarity(String left, String right) {
        String normalizedLeft = normalizeCompact(left);
        String normalizedRight = normalizeCompact(right);
        if (Math.min(normalizedLeft.length(), normalizedRight.length()) < 200) return 0;
        Set<String> leftGrams = grams(normalizedLeft, 4);
        Set<String> rightGrams = grams(normalizedRight, 4);
        Set<String> intersection = new HashSet<>(leftGrams);
        intersection.retainAll(rightGrams);
        Set<String> union = new HashSet<>(leftGrams);
        union.addAll(rightGrams);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private Set<String> grams(String value, int size) {
        Set<String> result = new HashSet<>();
        for (int index = 0; index <= value.length() - size; index++) {
            result.add(value.substring(index, index + size));
        }
        return result;
    }

    private String normalizeCompact(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private boolean isSpecificTopic(String topic) {
        String normalized = normalize(topic);
        if (normalized.isBlank() || BROAD_AREA_TOPICS.contains(normalized)) return false;

        Set<String> topicTerms = Arrays.stream(normalized.split("\\s+"))
                .map(this::stripParticle)
                .filter(term -> !term.isBlank())
                .collect(Collectors.toSet());
        Set<String> specificTerms = topicTerms.stream()
                .filter(term -> !BROAD_AREA_TERMS.contains(term))
                .collect(Collectors.toSet());
        return specificTerms.size() >= 2;
    }

    boolean similarTitle(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) return false;
        if (Math.min(normalizedLeft.length(), normalizedRight.length()) >= 8
                && (normalizedLeft.contains(normalizedRight)
                || normalizedRight.contains(normalizedLeft))) return true;

        Set<String> leftTerms = terms(left);
        Set<String> rightTerms = terms(right);
        if (leftTerms.isEmpty() || rightTerms.isEmpty()) return false;
        Set<String> common = new HashSet<>(leftTerms);
        common.retainAll(rightTerms);
        int smallerSize = Math.min(leftTerms.size(), rightTerms.size());
        return common.size() >= 2 || common.size() * 4 >= smallerSize * 3;
    }

    private Set<String> terms(String value) {
        return Arrays.stream(normalize(value).split("\\s+"))
                .map(this::stripParticle)
                .filter(term -> term.length() >= 2)
                .filter(term -> !GENERIC_TERMS.contains(term))
                .collect(Collectors.toSet());
    }

    private String stripParticle(String term) {
        for (String particle : PARTICLES) {
            if (term.length() > particle.length() + 1 && term.endsWith(particle)) {
                return term.substring(0, term.length() - particle.length());
            }
        }
        return term;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }
}

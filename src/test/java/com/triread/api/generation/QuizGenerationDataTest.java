package com.triread.api.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.triread.api.admin.AdminQuizService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuizGenerationDataTest {

    @Test
    void convertsGeneratedQuizToCreateCommand() {
        QuizGenerationData.GeneratedQuestion question =
                new QuizGenerationData.GeneratedQuestion(
                        "question",
                        List.of("one", "two", "three", "four"),
                        2,
                        "explanation",
                        "evidence",
                        "DETAIL",
                        List.of()
                );
        QuizGenerationData.GeneratedPassage passage =
                new QuizGenerationData.GeneratedPassage(
                        "title", "topic", "content", List.of(question));
        QuizGenerationData.GeneratedQuiz quiz =
                new QuizGenerationData.GeneratedQuiz(
                        LocalDate.of(2026, 7, 29), List.of(passage));

        AdminQuizService.CreateQuiz command = quiz.toCreateQuiz();

        assertThat(command.challengeDate()).isEqualTo(LocalDate.of(2026, 7, 29));
        assertThat(command.passages()).singleElement().satisfies(createdPassage -> {
            assertThat(createdPassage.title()).isEqualTo("title");
            assertThat(createdPassage.questions()).singleElement()
                    .satisfies(createdQuestion -> {
                        assertThat(createdQuestion.content()).isEqualTo("question");
                        assertThat(createdQuestion.correctOptionPosition()).isEqualTo(2);
                    });
        });
    }

    @Test
    void sourceBriefIsGroundedWithTwoDifferentVerifiedUrlsPerPassage() {
        List<QuizGenerationData.ContentSource> sources = new ArrayList<>();
        for (int position = 1; position <= 3; position++) {
            sources.add(source(position, "https://example.com/" + position + "/a", true));
            sources.add(source(position, "https://example.com/" + position + "/b", true));
        }
        QuizGenerationData.SourceBrief brief = new QuizGenerationData.SourceBrief(
                1L, LocalDate.of(2026, 7, 29), "READY",
                "model", "briefing", null, sources);

        assertThat(brief.grounded()).isTrue();
    }

    @Test
    void duplicateOrUnverifiedUrlsDoNotCountAsGroundedSources() {
        List<QuizGenerationData.ContentSource> sources = new ArrayList<>();
        for (int position = 1; position <= 3; position++) {
            sources.add(source(position, "https://example.com/" + position, true));
            sources.add(source(position, "https://example.com/" + position, true));
            sources.add(source(position, "https://other.com/" + position, false));
        }
        QuizGenerationData.SourceBrief brief = new QuizGenerationData.SourceBrief(
                1L, LocalDate.of(2026, 7, 29), "READY",
                "model", "briefing", null, sources);

        assertThat(brief.grounded()).isFalse();
    }

    private QuizGenerationData.ContentSource source(
            int position, String url, boolean verified) {
        return new QuizGenerationData.ContentSource(
                position,
                1L,
                position,
                "title",
                "publisher",
                LocalDate.of(2026, 7, 28),
                url,
                "summary",
                Instant.parse("2026-07-29T00:00:00Z"),
                verified
        );
    }
}

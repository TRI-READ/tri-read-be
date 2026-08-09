package com.triread.api.quality;

import java.time.LocalDate;

public final class QuizQualityData {
    private QuizQualityData() {
    }

    public record QuestionRow(
            long questionId,
            LocalDate challengeDate,
            String variantCode,
            String passageTitle,
            String topic,
            int passagePosition,
            int questionPosition,
            String questionContent,
            long responseCount,
            long correctCount,
            long incorrectCount,
            double correctRate,
            long maxWrongOptionCount,
            String qualityStatus
    ) {
    }

    public record OptionRow(
            long optionId,
            long questionId,
            int position,
            String content,
            long selectedCount,
            double selectionRate,
            boolean correct
    ) {
    }

    public record StatusCount(String qualityStatus, long count) {
    }
}

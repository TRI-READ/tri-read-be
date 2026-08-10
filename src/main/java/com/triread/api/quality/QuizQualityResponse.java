package com.triread.api.quality;

import com.triread.api.common.PageResponse;
import java.time.LocalDate;
import java.util.List;

public final class QuizQualityResponse {
    private QuizQualityResponse() {
    }

    public record QualityPage(
            PageResponse<QuestionQuality> page,
            long totalQuestionCount,
            long reviewRequiredCount,
            long dataInsufficientCount,
            long normalCount
    ) {
    }

    public record QuestionQuality(
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
            String status,
            List<String> reasons,
            List<OptionQuality> options
    ) {
    }

    public record OptionQuality(
            long optionId,
            int position,
            String content,
            long selectedCount,
            double selectionRate,
            boolean correct
    ) {
    }
}

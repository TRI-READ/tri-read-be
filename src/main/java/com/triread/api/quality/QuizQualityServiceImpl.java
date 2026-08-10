package com.triread.api.quality;

import com.triread.api.common.PageResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizQualityServiceImpl implements QuizQualityService {
    private final QuizQualityMapper mapper;

    public QuizQualityServiceImpl(QuizQualityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public QuizQualityResponse.QualityPage getQualityPage(
            int requestedPage,
            int requestedSize,
            String requestedStatus,
            String requestedKeyword
    ) {
        int page = PageResponse.page(requestedPage);
        int size = PageResponse.size(requestedSize);
        String status = normalizeStatus(requestedStatus);
        String keyword = normalizeKeyword(requestedKeyword);

        long totalElements = mapper.countQuestions(status, keyword);
        List<QuizQualityData.QuestionRow> rows = mapper.findQuestions(
                status, keyword, page * size, size);
        Map<Long, List<QuizQualityData.OptionRow>> optionsByQuestion = loadOptions(rows);

        List<QuizQualityResponse.QuestionQuality> items = new ArrayList<>();
        for (QuizQualityData.QuestionRow row : rows) {
            List<QuizQualityData.OptionRow> optionRows = optionsByQuestion.getOrDefault(
                    row.questionId(), Collections.emptyList());
            items.add(toQuestionQuality(row, optionRows));
        }

        Map<String, Long> counts = new HashMap<>();
        for (QuizQualityData.StatusCount count : mapper.countByStatus()) {
            counts.put(count.qualityStatus(), count.count());
        }

        long reviewRequiredCount = counts.getOrDefault("REVIEW_REQUIRED", 0L);
        long dataInsufficientCount = counts.getOrDefault("DATA_INSUFFICIENT", 0L);
        long normalCount = counts.getOrDefault("NORMAL", 0L);

        return new QuizQualityResponse.QualityPage(
                PageResponse.of(items, page, size, totalElements),
                reviewRequiredCount + dataInsufficientCount + normalCount,
                reviewRequiredCount,
                dataInsufficientCount,
                normalCount
        );
    }

    private Map<Long, List<QuizQualityData.OptionRow>> loadOptions(
            List<QuizQualityData.QuestionRow> rows
    ) {
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> questionIds = rows.stream()
                .map(QuizQualityData.QuestionRow::questionId)
                .toList();
        Map<Long, List<QuizQualityData.OptionRow>> result = new HashMap<>();
        for (QuizQualityData.OptionRow option : mapper.findOptions(questionIds)) {
            result.computeIfAbsent(option.questionId(), ignored -> new ArrayList<>()).add(option);
        }
        return result;
    }

    private QuizQualityResponse.QuestionQuality toQuestionQuality(
            QuizQualityData.QuestionRow row,
            List<QuizQualityData.OptionRow> optionRows
    ) {
        List<QuizQualityResponse.OptionQuality> options = optionRows.stream()
                .map(option -> new QuizQualityResponse.OptionQuality(
                        option.optionId(), option.position(), option.content(),
                        option.selectedCount(), option.selectionRate(), option.correct()))
                .toList();

        return new QuizQualityResponse.QuestionQuality(
                row.questionId(), row.challengeDate(), row.variantCode(),
                row.passageTitle(), row.topic(), row.passagePosition(),
                row.questionPosition(), row.questionContent(), row.responseCount(),
                row.correctCount(), row.incorrectCount(), row.correctRate(),
                row.qualityStatus(), buildReasons(row, optionRows), options
        );
    }

    private List<String> buildReasons(
            QuizQualityData.QuestionRow row,
            List<QuizQualityData.OptionRow> options
    ) {
        List<String> reasons = new ArrayList<>();
        if (row.responseCount() < 5) {
            reasons.add("아직 응답이 5건 미만입니다.");
            return reasons;
        }
        if (row.correctRate() < 30) {
            reasons.add("정답률이 " + percentage(row.correctRate()) + "%로 너무 낮습니다.");
        }
        if (row.correctRate() > 90) {
            reasons.add("정답률이 " + percentage(row.correctRate()) + "%로 너무 높습니다.");
        }
        if (row.incorrectCount() >= 3
                && row.maxWrongOptionCount() * 100.0 / row.incorrectCount() >= 70) {
            QuizQualityData.OptionRow option = findMostSelectedWrongOption(options);
            if (option != null) {
                double rate = option.selectedCount() * 100.0 / row.incorrectCount();
                reasons.add(option.position() + "번 오답에 오답 응답의 "
                        + percentage(rate) + "%가 집중되었습니다.");
            }
        }
        return reasons;
    }

    private QuizQualityData.OptionRow findMostSelectedWrongOption(
            List<QuizQualityData.OptionRow> options
    ) {
        QuizQualityData.OptionRow result = null;
        for (QuizQualityData.OptionRow option : options) {
            if (!option.correct()
                    && (result == null || option.selectedCount() > result.selectedCount())) {
                result = option;
            }
        }
        return result;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status.trim().toUpperCase()) {
            case "NORMAL", "REVIEW_REQUIRED", "DATA_INSUFFICIENT" -> status.trim().toUpperCase();
            default -> null;
        };
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private long percentage(double value) {
        return Math.round(value);
    }
}

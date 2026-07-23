package com.triread.api.generation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QuizGenerationMapper {
    void insertLog(QuizGenerationData.GenerationLogInsert log);
    void updateLog(@Param("generationLogId") long generationLogId,
                   @Param("quizSetId") Long quizSetId,
                   @Param("status") String status,
                   @Param("attemptCount") int attemptCount,
                   @Param("validationScore") Integer validationScore,
                   @Param("rawResponse") String rawResponse,
                   @Param("errorMessage") String errorMessage,
                   @Param("completedAt") Instant completedAt,
                   @Param("updatedAt") Instant updatedAt);
    void insertValidationResult(QuizGenerationData.ValidationResultInsert result);
    List<QuizGenerationData.GenerationLogRow> findLogs(@Param("status") String status,
                                                        @Param("targetDate") LocalDate targetDate,
                                                        @Param("offset") int offset,
                                                        @Param("limit") int limit);
    long countLogs(@Param("status") String status, @Param("targetDate") LocalDate targetDate);
    long countLogsCreatedBetween(@Param("from") Instant from,
                                 @Param("until") Instant until);
    QuizGenerationData.GenerationStats getStats();
    QuizGenerationData.GenerationLogRow findLog(long generationLogId);
    List<QuizGenerationData.ValidationResultRow> findValidationResults(long generationLogId);
    List<QuizGenerationData.RecentPassageRow> findRecentPassages(
            @Param("targetDate") LocalDate targetDate,
            @Param("sinceDate") LocalDate sinceDate
    );
    void insertApiCall(QuizGenerationData.ApiCallInsert call);
    void completeApiCall(@Param("apiCallId") long apiCallId,
                         @Param("status") String status,
                         @Param("errorCode") String errorCode,
                         @Param("completedAt") Instant completedAt);
    long countApiCallsCreatedBetween(@Param("from") Instant from, @Param("until") Instant until);
    QuizGenerationData.ApiUsageStats getApiUsageStats(@Param("from") Instant from,
                                                       @Param("until") Instant until);
    QuizGenerationData.SourceBriefRow findSourceBrief(LocalDate targetDate);
    List<QuizGenerationData.ContentSource> findSourcesByBrief(long sourceBriefId);
    List<QuizGenerationData.ContentSource> findSourcesByGenerationLog(long generationLogId);
    List<QuizGenerationData.ContentSource> findSourcesByPassage(long passageId);
    void insertSourceBrief(QuizGenerationData.SourceBriefInsert brief);
    void insertContentSource(QuizGenerationData.ContentSourceInsert source);
    void linkSourcesToQuiz(@Param("quizSetId") long quizSetId,
                           @Param("sourceBriefId") long sourceBriefId);
}

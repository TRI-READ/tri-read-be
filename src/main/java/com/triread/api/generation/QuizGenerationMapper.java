package com.triread.api.generation;

import java.time.Instant;
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
    List<QuizGenerationData.GenerationLogRow> findLogs();
    QuizGenerationData.GenerationLogRow findLog(long generationLogId);
    List<QuizGenerationData.ValidationResultRow> findValidationResults(long generationLogId);
}

package com.triread.api.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminQuizMapper {
    List<AdminQuizData.QuizRow> findQuizzes();
    AdminQuizData.QuizRow findQuiz(long quizSetId);
    int countActiveByDate(LocalDate challengeDate);
    List<String> findActiveVariantCodesByDate(LocalDate challengeDate);
    int insertQuiz(AdminQuizData.QuizInsert quiz);
    int insertPassage(AdminQuizData.PassageInsert passage);
    int insertQuestion(AdminQuizData.QuestionInsert question);
    int insertOption(AdminQuizData.OptionInsert option);
    int insertKey(@Param("questionId") long questionId,
                  @Param("correctOptionId") long correctOptionId,
                  @Param("explanation") String explanation,
                  @Param("evidence") String evidence);
    int publish(@Param("quizSetId") long quizSetId, @Param("publishedAt") Instant publishedAt);
    int markReviewed(@Param("quizSetId") long quizSetId,
                     @Param("aiProvider") String aiProvider,
                     @Param("aiModel") String aiModel,
                     @Param("promptVersion") String promptVersion);
    int updateDraftDate(@Param("quizSetId") long quizSetId,
                        @Param("challengeDate") LocalDate challengeDate,
                        @Param("variantCode") String variantCode);
    int rescheduleOldestUnassignedPublishedQuiz(@Param("currentDate") LocalDate currentDate,
                                                @Param("targetDate") LocalDate targetDate,
                                                @Param("variantCode") String variantCode);
    int invalidateGeneration(@Param("quizSetId") long quizSetId, @Param("updatedAt") Instant updatedAt);
    int deleteKeys(long quizSetId);
    int deleteOptions(long quizSetId);
    int deleteQuestions(long quizSetId);
    int deletePassages(long quizSetId);
    int deleteDraft(long quizSetId);
}

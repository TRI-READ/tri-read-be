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
    int countPublishedByDate(LocalDate challengeDate);
    int insertQuiz(AdminQuizData.QuizInsert quiz);
    int insertPassage(AdminQuizData.PassageInsert passage);
    int insertQuestion(AdminQuizData.QuestionInsert question);
    int insertOption(AdminQuizData.OptionInsert option);
    int insertKey(@Param("questionId") long questionId,
                  @Param("correctOptionId") long correctOptionId,
                  @Param("explanation") String explanation,
                  @Param("evidence") String evidence);
    int publish(@Param("quizSetId") long quizSetId, @Param("publishedAt") Instant publishedAt);
}

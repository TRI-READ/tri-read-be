package com.triread.api.quiz;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QuizMapper {

    QuizData.QuizSetRow findTodayQuiz(
            @Param("challengeDate") LocalDate challengeDate,
            @Param("userId") long userId
    );

    List<QuizData.AttemptRow> findAttempts(
            @Param("quizSetId") long quizSetId,
            @Param("userId") long userId
    );

    List<Long> findPublishedQuizSetIds(
            @Param("challengeDate") LocalDate challengeDate,
            @Param("userId") long userId
    );

    int insertAssignment(@Param("userId") long userId,
                         @Param("challengeDate") LocalDate challengeDate,
                         @Param("quizSetId") long quizSetId);

    List<QuizData.PassageRow> findPassages(long quizSetId);

    List<QuizData.QuestionRow> findQuestions(long quizSetId);

    List<QuizData.OptionRow> findOptions(long quizSetId);

    List<QuizData.AnswerKeyRow> findAnswerKeys(long quizSetId);

    List<QuizService.SourceReference> findSourceReferences(long passageId);

    int insertAttempt(QuizData.QuizAttemptInsert attempt);

    int insertAttemptAnswers(
            @Param("answers") List<QuizData.AttemptAnswerInsert> answers
    );

    int insertAnswerReviews(
            @Param("reviews") List<QuizData.AnswerReviewInsert> reviews
    );
}

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

    List<Long> findAvailableQuizSetIds(
            @Param("studyDate") LocalDate studyDate,
            @Param("userId") long userId
    );

    QuizData.QuizSetRow findAssignedQuiz(
            @Param("quizSetId") long quizSetId,
            @Param("userId") long userId
    );

    List<QuizData.QuizSetRow> findBonusQuizSets(
            @Param("userId") long userId,
            @Param("beforeDate") LocalDate beforeDate
    );

    int carryForwardOldestUnstartedAssignment(
            @Param("userId") long userId,
            @Param("studyDate") LocalDate studyDate
    );

    int insertAssignment(@Param("userId") long userId,
                         @Param("studyDate") LocalDate studyDate,
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

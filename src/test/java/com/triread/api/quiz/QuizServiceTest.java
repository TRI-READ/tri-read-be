package com.triread.api.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    private static final long USER_ID = 3L;
    private static final long QUIZ_SET_ID = 17L;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 9);
    private static final Instant NOW = Instant.parse("2026-07-09T03:00:00Z");

    @Mock
    private QuizMapper quizMapper;

    private QuizService quizService;
    private List<QuizData.QuestionRow> questions;
    private List<QuizData.OptionRow> options;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        quizService = new QuizService(quizMapper, clock);
        questions = completeQuestions();
        options = completeOptions(questions);
    }

    @Test
    void todayReturnsThreePassagesAndNineQuestionsWithoutAnswerKeys() {
        givenUncompletedTodayQuiz();
        givenCompleteContent();

        QuizService.TodayQuizResponse result = quizService.getTodayQuiz(USER_ID);

        assertThat(result.quizSetId()).isEqualTo(QUIZ_SET_ID);
        assertThat(result.challengeDate()).isEqualTo(TODAY);
        assertThat(result.attempt()).isNull();
        assertThat(result.attempts()).isEmpty();
        assertThat(result.bonusUnlocked()).isFalse();
        assertThat(result.passages()).hasSize(3);
        assertThat(result.passages())
                .allSatisfy(passage -> {
                    assertThat(passage.questions()).hasSize(3);
                    assertThat(passage.questions())
                            .allSatisfy(question -> assertThat(question.options()).hasSize(4));
                });
        verify(quizMapper, never()).findAnswerKeys(any(Long.class));
    }

    @Test
    void todayIncludesCompletedAttemptSummary() {
        when(quizMapper.findTodayQuiz(TODAY, USER_ID)).thenReturn(
                quizSet(TODAY, "A")
        );
        when(quizMapper.findAttempts(QUIZ_SET_ID, USER_ID)).thenReturn(
                List.of(new QuizData.AttemptRow(
                        91L, 3, 3, 101L, "PRIMARY", NOW
                ))
        );
        givenCompleteContent();

        QuizService.TodayQuizResponse result = quizService.getTodayQuiz(USER_ID);

        assertThat(result.attempt()).isEqualTo(
                new QuizService.AttemptSummary(91L, 3, 3, 101L, "PRIMARY", NOW)
        );
        assertThat(result.bonusUnlocked()).isTrue();
    }

    @Test
    void assignsOnePublishedVariantOnFirstVisit() {
        QuizData.QuizSetRow assigned = quizSet(TODAY, "B");
        when(quizMapper.findTodayQuiz(TODAY, USER_ID)).thenReturn(null, assigned);
        when(quizMapper.findPublishedQuizSetIds(TODAY, USER_ID))
                .thenReturn(List.of(16L, QUIZ_SET_ID, 18L));
        givenCompleteContent();

        QuizService.TodayQuizResponse result = quizService.getTodayQuiz(USER_ID);

        assertThat(result.variantCode()).isEqualTo("B");
        verify(quizMapper).insertAssignment(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(TODAY),
                org.mockito.ArgumentMatchers.longThat(id ->
                        id == 16L || id == QUIZ_SET_ID || id == 18L));
    }

    @Test
    void submitScoresAnswersAndCreatesReviewsOnlyForWrongAnswers() {
        givenUncompletedTodayQuiz();
        givenCompleteContent();
        when(quizMapper.findAnswerKeys(QUIZ_SET_ID)).thenReturn(answerKeys(questions));
        doAnswer(invocation -> {
            QuizData.QuizAttemptInsert attempt = invocation.getArgument(0);
            attempt.setId(55L);
            return 1;
        }).when(quizMapper).insertAttempt(any(QuizData.QuizAttemptInsert.class));

        List<QuizService.SubmittedAnswer> submittedAnswers = submittedAnswers(2);
        QuizService.QuizResultResponse result =
                quizService.submitAttempt(USER_ID, QUIZ_SET_ID, submittedAnswers);

        assertThat(result.attemptId()).isEqualTo(55L);
        assertThat(result.score()).isEqualTo(2);
        assertThat(result.totalQuestions()).isEqualTo(3);
        assertThat(result.wrongCount()).isEqualTo(1);
        assertThat(result.answers()).hasSize(3);
        assertThat(result.answers()).filteredOn(QuizService.QuestionResult::correct).hasSize(2);
        assertThat(result.passageId()).isEqualTo(101L);
        assertThat(result.attemptType()).isEqualTo("PRIMARY");
        verify(quizMapper).insertAttemptAnswers(argThat(answers ->
                answers.size() == 3
                        && answers.stream().allMatch(answer -> answer.attemptId() == 55L)
        ));
        verify(quizMapper).insertAnswerReviews(argThat(reviews ->
                reviews.size() == 1
                        && reviews.stream().allMatch(review ->
                        review.userId() == USER_ID && review.sourceAttemptId() == 55L
                )
        ));
    }

    @Test
    void submitRejectsOptionFromAnotherQuestion() {
        givenUncompletedTodayQuiz();
        givenCompleteContent();
        List<QuizService.SubmittedAnswer> submittedAnswers = submittedAnswers(3);
        QuizService.SubmittedAnswer first = submittedAnswers.getFirst();
        submittedAnswers.set(0, new QuizService.SubmittedAnswer(
                first.questionId(),
                options.get(4).optionId()
        ));

        assertThatThrownBy(() ->
                quizService.submitAttempt(USER_ID, QUIZ_SET_ID, submittedAnswers)
        ).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(exception.getCode()).isEqualTo("INVALID_QUIZ_ANSWERS");
        });

        verify(quizMapper, never()).insertAttempt(any());
    }

    @Test
    void submitRejectsAlreadyCompletedQuiz() {
        when(quizMapper.findTodayQuiz(TODAY, USER_ID)).thenReturn(
                quizSet(TODAY, "A")
        );
        givenCompleteContent();
        when(quizMapper.findAttempts(QUIZ_SET_ID, USER_ID)).thenReturn(
                List.of(new QuizData.AttemptRow(
                        91L, 3, 3, 101L, "PRIMARY", NOW
                ))
        );

        assertThatThrownBy(() ->
                quizService.submitAttempt(USER_ID, QUIZ_SET_ID, submittedAnswers(3))
        ).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getCode()).isEqualTo("QUIZ_ALREADY_COMPLETED");
        });
    }

    @Test
    void todayRejectsIncompletePublishedContent() {
        givenUncompletedTodayQuiz();
        when(quizMapper.findPassages(QUIZ_SET_ID)).thenReturn(completePassages());
        when(quizMapper.findQuestions(QUIZ_SET_ID)).thenReturn(questions.subList(0, 8));
        when(quizMapper.findOptions(QUIZ_SET_ID)).thenReturn(options);

        assertThatThrownBy(() -> quizService.getTodayQuiz(USER_ID))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(
                            HttpStatus.INTERNAL_SERVER_ERROR
                    );
                    assertThat(exception.getCode()).isEqualTo("QUIZ_CONTENT_INVALID");
                });
    }

    @Test
    void submitRejectsAnswersMixedFromDifferentPassages() {
        givenUncompletedTodayQuiz();
        givenCompleteContent();
        List<QuizService.SubmittedAnswer> submittedAnswers = submittedAnswers(3);
        QuizData.QuestionRow otherPassageQuestion = questions.get(3);
        submittedAnswers.set(2, new QuizService.SubmittedAnswer(
                otherPassageQuestion.questionId(),
                otherPassageQuestion.questionId() * 10 + 1
        ));

        assertThatThrownBy(() ->
                quizService.submitAttempt(USER_ID, QUIZ_SET_ID, submittedAnswers)
        ).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("INVALID_QUIZ_ANSWERS"));

        verify(quizMapper, never()).insertAttempt(any());
    }

    @Test
    void submitUnlocksASecondPassageAsBonusAfterPrimaryCompletion() {
        givenUncompletedTodayQuiz();
        givenCompleteContent();
        when(quizMapper.findAttempts(QUIZ_SET_ID, USER_ID)).thenReturn(
                List.of(new QuizData.AttemptRow(
                        91L, 2, 3, 101L, "PRIMARY", NOW.minusSeconds(300)
                ))
        );
        when(quizMapper.findAnswerKeys(QUIZ_SET_ID)).thenReturn(answerKeys(questions));
        doAnswer(invocation -> {
            QuizData.QuizAttemptInsert attempt = invocation.getArgument(0);
            attempt.setId(92L);
            return 1;
        }).when(quizMapper).insertAttempt(any(QuizData.QuizAttemptInsert.class));

        QuizService.QuizResultResponse result = quizService.submitAttempt(
                USER_ID,
                QUIZ_SET_ID,
                submittedAnswersForPassage(1, 3)
        );

        assertThat(result.passageId()).isEqualTo(102L);
        assertThat(result.attemptType()).isEqualTo("BONUS");
        verify(quizMapper).insertAttempt(argThat(attempt ->
                attempt.getPassageId() == 102L
                        && "BONUS".equals(attempt.getAttemptType())
        ));
    }

    @Test
    void saturdayUsesThePreviousFridayQuiz() {
        LocalDate friday = LocalDate.of(2026, 7, 17);
        Clock weekendClock = Clock.fixed(
                Instant.parse("2026-07-18T03:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        quizService = new QuizService(quizMapper, weekendClock);
        when(quizMapper.findTodayQuiz(friday, USER_ID)).thenReturn(quizSet(friday, "A"));
        givenCompleteContent();

        QuizService.TodayQuizResponse result = quizService.getTodayQuiz(USER_ID);

        assertThat(result.challengeDate()).isEqualTo(friday);
        verify(quizMapper).findTodayQuiz(friday, USER_ID);
    }

    private void givenUncompletedTodayQuiz() {
        when(quizMapper.findTodayQuiz(TODAY, USER_ID)).thenReturn(
                quizSet(TODAY, "A")
        );
    }

    private QuizData.QuizSetRow quizSet(LocalDate challengeDate, String variantCode) {
        return new QuizData.QuizSetRow(
                QUIZ_SET_ID,
                challengeDate,
                variantCode,
                "HIGH_SCHOOL_GRADE_3"
        );
    }

    private void givenCompleteContent() {
        when(quizMapper.findPassages(QUIZ_SET_ID)).thenReturn(completePassages());
        when(quizMapper.findQuestions(QUIZ_SET_ID)).thenReturn(questions);
        when(quizMapper.findOptions(QUIZ_SET_ID)).thenReturn(options);
    }

    private List<QuizData.PassageRow> completePassages() {
        return List.of(
                new QuizData.PassageRow(101L, (short) 1, "Passage 1", "Content 1", "Science"),
                new QuizData.PassageRow(102L, (short) 2, "Passage 2", "Content 2", "Society"),
                new QuizData.PassageRow(103L, (short) 3, "Passage 3", "Content 3", "Philosophy")
        );
    }

    private List<QuizData.QuestionRow> completeQuestions() {
        List<QuizData.QuestionRow> rows = new ArrayList<>();
        long questionId = 1001L;
        for (long passageId = 101L; passageId <= 103L; passageId++) {
            for (short position = 1; position <= 3; position++) {
                rows.add(new QuizData.QuestionRow(
                        questionId++,
                        passageId,
                        position,
                        "Question " + position
                ));
            }
        }
        return rows;
    }

    private List<QuizData.OptionRow> completeOptions(
            List<QuizData.QuestionRow> questionRows
    ) {
        List<QuizData.OptionRow> rows = new ArrayList<>();
        for (QuizData.QuestionRow question : questionRows) {
            for (short position = 1; position <= 4; position++) {
                rows.add(new QuizData.OptionRow(
                        question.questionId() * 10 + position,
                        question.questionId(),
                        position,
                        "Option " + position
                ));
            }
        }
        return rows;
    }

    private List<QuizData.AnswerKeyRow> answerKeys(
            List<QuizData.QuestionRow> questionRows
    ) {
        return questionRows.stream()
                .map(question -> new QuizData.AnswerKeyRow(
                        question.questionId(),
                        question.questionId() * 10 + 1,
                        "Explanation",
                        "Evidence"
                ))
                .toList();
    }

    private List<QuizService.SubmittedAnswer> submittedAnswers(int correctCount) {
        return submittedAnswersForPassage(0, correctCount);
    }

    private List<QuizService.SubmittedAnswer> submittedAnswersForPassage(
            int passageIndex,
            int correctCount
    ) {
        List<QuizService.SubmittedAnswer> answers = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            long questionId = questions.get(passageIndex * 3 + index).questionId();
            long selectedOptionId = questionId * 10 + (index < correctCount ? 1 : 2);
            answers.add(new QuizService.SubmittedAnswer(questionId, selectedOptionId));
        }
        return answers;
    }
}

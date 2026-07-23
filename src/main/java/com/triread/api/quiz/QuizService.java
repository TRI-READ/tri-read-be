package com.triread.api.quiz;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizService {

    private static final int PASSAGE_COUNT = 3;
    private static final int QUESTIONS_PER_PASSAGE = 3;
    private static final int OPTIONS_PER_QUESTION = 4;
    private static final int TOTAL_QUESTIONS = PASSAGE_COUNT * QUESTIONS_PER_PASSAGE;

    private final QuizMapper quizMapper;
    private final Clock clock;

    public QuizService(QuizMapper quizMapper, Clock clock) {
        this.quizMapper = quizMapper;
        this.clock = clock;
    }

    @Transactional
    public TodayQuizResponse getTodayQuiz(long userId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate challengeDate = resolveChallengeDate(today);
        QuizData.QuizSetRow quizSet = findTodayQuiz(userId, challengeDate);
        QuizContent content = loadAndValidateContent(quizSet.quizSetId());
        List<AttemptSummary> attempts = findAttemptSummaries(userId, quizSet.quizSetId());
        AttemptSummary primaryAttempt = attempts.stream()
                .filter(attempt -> "PRIMARY".equals(attempt.attemptType()))
                .findFirst()
                .orElse(null);

        Set<Long> completedPassageIds = attempts.stream()
                .map(AttemptSummary::passageId).collect(java.util.stream.Collectors.toSet());
        List<PassageResponse> visiblePassages = content.passages().stream()
                .map(passage -> completedPassageIds.contains(passage.passageId())
                        ? passage.withSources(quizMapper.findSourceReferences(passage.passageId()))
                        : passage)
                .toList();
        return new TodayQuizResponse(
                quizSet.quizSetId(),
                quizSet.challengeDate(),
                quizSet.variantCode(),
                quizSet.difficulty(),
                primaryAttempt,
                attempts,
                primaryAttempt != null,
                visiblePassages
        );
    }

    @Transactional
    public QuizResultResponse submitAttempt(
            long userId,
            long quizSetId,
            List<SubmittedAnswer> submittedAnswers
    ) {
        LocalDate today = LocalDate.now(clock);
        LocalDate challengeDate = resolveChallengeDate(today);
        QuizData.QuizSetRow quizSet = findTodayQuiz(userId, challengeDate);
        if (quizSet.quizSetId() != quizSetId) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TODAY_QUIZ_NOT_FOUND",
                    "Today's published quiz was not found."
            );
        }
        QuizContent content = loadAndValidateContent(quizSetId);
        ValidatedSubmission submission = validateSubmittedAnswers(submittedAnswers, content);
        List<QuizData.AttemptRow> existingAttempts = quizMapper.findAttempts(quizSetId, userId);
        if (existingAttempts.stream().anyMatch(
                attempt -> attempt.passageId() == submission.passageId())) {
            throw alreadyCompletedException();
        }
        if (existingAttempts.size() >= PASSAGE_COUNT) {
            throw alreadyCompletedException();
        }
        String attemptType = existingAttempts.isEmpty() ? "PRIMARY" : "BONUS";

        List<QuizData.AnswerKeyRow> answerKeys = quizMapper.findAnswerKeys(quizSetId);
        if (answerKeys.size() != TOTAL_QUESTIONS) {
            throw invalidQuizContentException();
        }

        Map<Long, SubmittedAnswer> answersByQuestion = submission.answersByQuestion();
        List<QuizData.AnswerKeyRow> selectedAnswerKeys = answerKeys.stream()
                .filter(answerKey -> answersByQuestion.containsKey(answerKey.questionId()))
                .toList();
        if (selectedAnswerKeys.size() != QUESTIONS_PER_PASSAGE) {
            throw invalidAnswersException();
        }
        List<QuizData.AttemptAnswerInsert> answersToInsert = new ArrayList<>();
        List<QuestionResult> questionResults = new ArrayList<>();
        int score = 0;

        for (QuizData.AnswerKeyRow answerKey : selectedAnswerKeys) {
            SubmittedAnswer submittedAnswer = answersByQuestion.get(answerKey.questionId());
            boolean correct = submittedAnswer.selectedOptionId() == answerKey.correctOptionId();
            if (correct) {
                score++;
            }

            answersToInsert.add(new QuizData.AttemptAnswerInsert(
                    0,
                    answerKey.questionId(),
                    submittedAnswer.selectedOptionId(),
                    correct
            ));
            questionResults.add(new QuestionResult(
                    answerKey.questionId(),
                    submittedAnswer.selectedOptionId(),
                    answerKey.correctOptionId(),
                    correct,
                    answerKey.explanation(),
                    answerKey.evidence()
            ));
        }

        Instant completedAt = clock.instant();
        QuizData.QuizAttemptInsert attempt =
                new QuizData.QuizAttemptInsert(
                        userId,
                        quizSetId,
                        submission.passageId(),
                        attemptType,
                        score,
                        completedAt
                );
        try {
            quizMapper.insertAttempt(attempt);
        } catch (DataIntegrityViolationException exception) {
            throw alreadyCompletedException();
        }

        List<QuizData.AttemptAnswerInsert> persistedAnswers = answersToInsert.stream()
                .map(answer -> new QuizData.AttemptAnswerInsert(
                        attempt.getId(),
                        answer.questionId(),
                        answer.selectedOptionId(),
                        answer.correct()
                ))
                .toList();
        quizMapper.insertAttemptAnswers(persistedAnswers);

        List<QuizData.AnswerReviewInsert> reviews = persistedAnswers.stream()
                .filter(answer -> !answer.correct())
                .map(answer -> new QuizData.AnswerReviewInsert(
                        userId,
                        answer.questionId(),
                        attempt.getId()
                ))
                .toList();
        if (!reviews.isEmpty()) {
            quizMapper.insertAnswerReviews(reviews);
        }

        return new QuizResultResponse(
                attempt.getId(),
                quizSetId,
                submission.passageId(),
                attemptType,
                score,
                QUESTIONS_PER_PASSAGE,
                QUESTIONS_PER_PASSAGE - score,
                completedAt,
                questionResults,
                quizMapper.findSourceReferences(submission.passageId())
        );
    }

    private QuizData.QuizSetRow findTodayQuiz(long userId, LocalDate challengeDate) {
        QuizData.QuizSetRow quizSet = quizMapper.findTodayQuiz(challengeDate, userId);
        if (quizSet == null) {
            List<Long> candidates = quizMapper.findPublishedQuizSetIds(challengeDate, userId);
            if (!candidates.isEmpty()) {
                int assignmentIndex = Math.floorMod(
                        31 * Long.hashCode(userId) + challengeDate.hashCode(), candidates.size());
                quizMapper.insertAssignment(
                        userId,
                        challengeDate,
                        candidates.get(assignmentIndex)
                );
                quizSet = quizMapper.findTodayQuiz(challengeDate, userId);
            }
        }
        if (quizSet == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TODAY_QUIZ_NOT_FOUND",
                    "Today's published quiz was not found."
            );
        }
        return quizSet;
    }

    private List<AttemptSummary> findAttemptSummaries(long userId, long quizSetId) {
        return quizMapper.findAttempts(quizSetId, userId).stream()
                .map(attempt -> new AttemptSummary(
                        attempt.attemptId(),
                        attempt.score(),
                        attempt.totalQuestions(),
                        attempt.passageId(),
                        attempt.attemptType(),
                        attempt.completedAt()
                ))
                .toList();
    }

    private LocalDate resolveChallengeDate(LocalDate today) {
        if (today.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return today.minusDays(1);
        }
        if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return today.minusDays(2);
        }
        return today;
    }

    private QuizContent loadAndValidateContent(long quizSetId) {
        List<QuizData.PassageRow> passages = quizMapper.findPassages(quizSetId);
        List<QuizData.QuestionRow> questions = quizMapper.findQuestions(quizSetId);
        List<QuizData.OptionRow> options = quizMapper.findOptions(quizSetId);

        if (passages.size() != PASSAGE_COUNT
                || questions.size() != TOTAL_QUESTIONS
                || options.size() != TOTAL_QUESTIONS * OPTIONS_PER_QUESTION) {
            throw invalidQuizContentException();
        }

        Map<Long, List<OptionResponse>> optionsByQuestion = new LinkedHashMap<>();
        Map<Long, Set<Long>> optionIdsByQuestion = new HashMap<>();
        for (QuizData.OptionRow option : options) {
            optionsByQuestion.computeIfAbsent(option.questionId(), ignored -> new ArrayList<>())
                    .add(new OptionResponse(
                            option.optionId(),
                            option.position(),
                            option.content()
                    ));
            optionIdsByQuestion.computeIfAbsent(option.questionId(), ignored -> new HashSet<>())
                    .add(option.optionId());
        }

        Map<Long, List<QuestionResponse>> questionsByPassage = new LinkedHashMap<>();
        Map<Long, Long> passageIdByQuestion = new HashMap<>();
        for (QuizData.QuestionRow question : questions) {
            List<OptionResponse> questionOptions = optionsByQuestion.get(question.questionId());
            if (questionOptions == null || questionOptions.size() != OPTIONS_PER_QUESTION) {
                throw invalidQuizContentException();
            }

            questionsByPassage.computeIfAbsent(question.passageId(), ignored -> new ArrayList<>())
                    .add(new QuestionResponse(
                            question.questionId(),
                            question.position(),
                            question.content(),
                            questionOptions
                    ));
            passageIdByQuestion.put(question.questionId(), question.passageId());
        }

        List<PassageResponse> passageResponses = new ArrayList<>();
        for (QuizData.PassageRow passage : passages) {
            List<QuestionResponse> passageQuestions = questionsByPassage.get(passage.passageId());
            if (passageQuestions == null || passageQuestions.size() != QUESTIONS_PER_PASSAGE) {
                throw invalidQuizContentException();
            }

            passageResponses.add(new PassageResponse(
                    passage.passageId(),
                    passage.position(),
                    passage.title(),
                    passage.content(),
                    passage.topic(),
                    passageQuestions,
                    List.of()
            ));
        }

        return new QuizContent(passageResponses, optionIdsByQuestion, passageIdByQuestion);
    }

    private ValidatedSubmission validateSubmittedAnswers(
            List<SubmittedAnswer> submittedAnswers,
            QuizContent content
    ) {
        if (submittedAnswers == null || submittedAnswers.size() != QUESTIONS_PER_PASSAGE) {
            throw invalidAnswersException();
        }

        Map<Long, SubmittedAnswer> answersByQuestion = new HashMap<>();
        Long selectedPassageId = null;
        for (SubmittedAnswer answer : submittedAnswers) {
            Set<Long> optionIds = content.optionIdsByQuestion().get(answer.questionId());
            Long passageId = content.passageIdByQuestion().get(answer.questionId());
            if (optionIds == null
                    || passageId == null
                    || !optionIds.contains(answer.selectedOptionId())
                    || answersByQuestion.putIfAbsent(answer.questionId(), answer) != null) {
                throw invalidAnswersException();
            }
            if (selectedPassageId == null) {
                selectedPassageId = passageId;
            } else if (!selectedPassageId.equals(passageId)) {
                throw invalidAnswersException();
            }
        }

        if (answersByQuestion.size() != QUESTIONS_PER_PASSAGE || selectedPassageId == null) {
            throw invalidAnswersException();
        }
        return new ValidatedSubmission(answersByQuestion, selectedPassageId);
    }

    private ApiException invalidAnswersException() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_QUIZ_ANSWERS",
                "Exactly three valid answers from one passage are required."
        );
    }

    private ApiException invalidQuizContentException() {
        return new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "QUIZ_CONTENT_INVALID",
                "The published quiz does not contain 3 passages and 9 complete questions."
        );
    }

    private ApiException alreadyCompletedException() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "QUIZ_ALREADY_COMPLETED",
                "This quiz has already been completed."
        );
    }

    private record QuizContent(
            List<PassageResponse> passages,
            Map<Long, Set<Long>> optionIdsByQuestion,
            Map<Long, Long> passageIdByQuestion
    ) {
    }

    private record ValidatedSubmission(
            Map<Long, SubmittedAnswer> answersByQuestion,
            long passageId
    ) {
    }

    public record SubmittedAnswer(long questionId, long selectedOptionId) {
    }

    public record TodayQuizResponse(
            long quizSetId,
            LocalDate challengeDate,
            String variantCode,
            String difficulty,
            AttemptSummary attempt,
            List<AttemptSummary> attempts,
            boolean bonusUnlocked,
            List<PassageResponse> passages
    ) {
    }

    public record AttemptSummary(
            long attemptId,
            int score,
            int totalQuestions,
            long passageId,
            String attemptType,
            Instant completedAt
    ) {
    }

    public record PassageResponse(
            long passageId,
            short position,
            String title,
            String content,
            String topic,
            List<QuestionResponse> questions,
            List<SourceReference> sources
    ) {
        PassageResponse withSources(List<SourceReference> references) {
            return new PassageResponse(passageId, position, title, content, topic,
                    questions, references == null ? List.of() : List.copyOf(references));
        }
    }

    public record SourceReference(String title, String publisher, LocalDate publishedOn,
                                  String sourceUrl) {}

    public record QuestionResponse(
            long questionId,
            short position,
            String content,
            List<OptionResponse> options
    ) {
    }

    public record OptionResponse(
            long optionId,
            short position,
            String content
    ) {
    }

    public record QuizResultResponse(
            long attemptId,
            long quizSetId,
            long passageId,
            String attemptType,
            int score,
            int totalQuestions,
            int wrongCount,
            Instant completedAt,
            List<QuestionResult> answers,
            List<SourceReference> sources
    ) {
    }

    public record QuestionResult(
            long questionId,
            long selectedOptionId,
            long correctOptionId,
            boolean correct,
            String explanation,
            String evidence
    ) {
    }
}

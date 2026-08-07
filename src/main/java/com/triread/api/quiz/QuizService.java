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
    private static final String PRIMARY_ATTEMPT = "PRIMARY";
    private static final String BONUS_ATTEMPT = "BONUS";

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
        AttemptSummary primaryAttempt = findPrimaryAttempt(attempts);
        List<PassageResponse> visiblePassages = addSourcesToCompletedPassages(
                content.passages(), attempts);
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
        validateQuizSetId(quizSet, quizSetId);

        QuizContent content = loadAndValidateContent(quizSetId);
        ValidatedSubmission submission = validateSubmittedAnswers(submittedAnswers, content);
        List<QuizData.AttemptRow> existingAttempts = quizMapper.findAttempts(quizSetId, userId);
        String attemptType = determineAttemptType(existingAttempts, submission.passageId());

        List<QuizData.AnswerKeyRow> answerKeys = quizMapper.findAnswerKeys(quizSetId);
        if (answerKeys.size() != TOTAL_QUESTIONS) {
            throw invalidQuizContentException();
        }

        List<QuestionResult> questionResults = gradeAnswers(
                submission.answersByQuestion(), answerKeys);
        int score = countCorrectAnswers(questionResults);
        Instant completedAt = clock.instant();
        long attemptId = saveAttempt(userId, quizSetId, submission.passageId(),
                attemptType, score, completedAt);
        List<QuizData.AttemptAnswerInsert> persistedAnswers = createAttemptAnswers(
                attemptId, questionResults);
        quizMapper.insertAttemptAnswers(persistedAnswers);
        saveWrongAnswerReviews(userId, attemptId, persistedAnswers);

        return new QuizResultResponse(
                attemptId,
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

    private void validateQuizSetId(QuizData.QuizSetRow quizSet, long quizSetId) {
        if (quizSet.quizSetId() == quizSetId) {
            return;
        }
        throw new ApiException(
                HttpStatus.NOT_FOUND,
                "TODAY_QUIZ_NOT_FOUND",
                "Today's published quiz was not found."
        );
    }

    private String determineAttemptType(
            List<QuizData.AttemptRow> existingAttempts,
            long passageId
    ) {
        for (QuizData.AttemptRow attempt : existingAttempts) {
            if (attempt.passageId() == passageId) {
                throw alreadyCompletedException();
            }
        }
        if (existingAttempts.size() >= PASSAGE_COUNT) {
            throw alreadyCompletedException();
        }
        return existingAttempts.isEmpty() ? PRIMARY_ATTEMPT : BONUS_ATTEMPT;
    }

    private List<QuestionResult> gradeAnswers(
            Map<Long, SubmittedAnswer> answersByQuestion,
            List<QuizData.AnswerKeyRow> answerKeys
    ) {
        List<QuestionResult> results = new ArrayList<>();
        for (QuizData.AnswerKeyRow answerKey : answerKeys) {
            SubmittedAnswer answer = answersByQuestion.get(answerKey.questionId());
            if (answer == null) {
                continue;
            }
            boolean correct = answer.selectedOptionId() == answerKey.correctOptionId();
            results.add(new QuestionResult(
                    answerKey.questionId(),
                    answer.selectedOptionId(),
                    answerKey.correctOptionId(),
                    correct,
                    answerKey.explanation(),
                    answerKey.evidence()
            ));
        }
        if (results.size() != QUESTIONS_PER_PASSAGE) {
            throw invalidAnswersException();
        }
        return results;
    }

    private int countCorrectAnswers(List<QuestionResult> results) {
        int score = 0;
        for (QuestionResult result : results) {
            if (result.correct()) {
                score++;
            }
        }
        return score;
    }

    private long saveAttempt(
            long userId,
            long quizSetId,
            long passageId,
            String attemptType,
            int score,
            Instant completedAt
    ) {
        QuizData.QuizAttemptInsert attempt = new QuizData.QuizAttemptInsert(
                userId, quizSetId, passageId, attemptType, score, completedAt);
        try {
            quizMapper.insertAttempt(attempt);
        } catch (DataIntegrityViolationException exception) {
            throw alreadyCompletedException();
        }
        return attempt.getId();
    }

    private List<QuizData.AttemptAnswerInsert> createAttemptAnswers(
            long attemptId,
            List<QuestionResult> results
    ) {
        List<QuizData.AttemptAnswerInsert> answers = new ArrayList<>();
        for (QuestionResult result : results) {
            answers.add(new QuizData.AttemptAnswerInsert(
                    attemptId,
                    result.questionId(),
                    result.selectedOptionId(),
                    result.correct()
            ));
        }
        return answers;
    }

    private void saveWrongAnswerReviews(
            long userId,
            long attemptId,
            List<QuizData.AttemptAnswerInsert> answers
    ) {
        List<QuizData.AnswerReviewInsert> reviews = new ArrayList<>();
        for (QuizData.AttemptAnswerInsert answer : answers) {
            if (!answer.correct()) {
                reviews.add(new QuizData.AnswerReviewInsert(
                        userId, answer.questionId(), attemptId));
            }
        }
        if (!reviews.isEmpty()) {
            quizMapper.insertAnswerReviews(reviews);
        }
    }

    private QuizData.QuizSetRow findTodayQuiz(long userId, LocalDate challengeDate) {
        QuizData.QuizSetRow quizSet = quizMapper.findTodayQuiz(challengeDate, userId);
        if (quizSet != null) {
            return quizSet;
        }

        List<Long> candidates = quizMapper.findPublishedQuizSetIds(challengeDate, userId);
        if (!candidates.isEmpty()) {
            int assignmentIndex = Math.floorMod(
                    31 * Long.hashCode(userId) + challengeDate.hashCode(), candidates.size());
            long assignedQuizSetId = candidates.get(assignmentIndex);
            quizMapper.insertAssignment(userId, challengeDate, assignedQuizSetId);
            quizSet = quizMapper.findTodayQuiz(challengeDate, userId);
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

    private AttemptSummary findPrimaryAttempt(List<AttemptSummary> attempts) {
        for (AttemptSummary attempt : attempts) {
            if (PRIMARY_ATTEMPT.equals(attempt.attemptType())) {
                return attempt;
            }
        }
        return null;
    }

    private List<PassageResponse> addSourcesToCompletedPassages(
            List<PassageResponse> passages,
            List<AttemptSummary> attempts
    ) {
        Set<Long> completedPassageIds = new HashSet<>();
        for (AttemptSummary attempt : attempts) {
            completedPassageIds.add(attempt.passageId());
        }

        List<PassageResponse> visiblePassages = new ArrayList<>();
        for (PassageResponse passage : passages) {
            if (completedPassageIds.contains(passage.passageId())) {
                List<SourceReference> sources = quizMapper.findSourceReferences(
                        passage.passageId());
                visiblePassages.add(passage.withSources(sources));
            } else {
                visiblePassages.add(passage);
            }
        }
        return visiblePassages;
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

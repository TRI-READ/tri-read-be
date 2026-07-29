package com.triread.api.admin;

import com.triread.api.common.ApiException;
import com.triread.api.common.PageResponse;
import com.triread.api.quiz.QuizData;
import com.triread.api.quiz.QuizMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminQuizService {
    private static final int PASSAGE_COUNT = 3;
    private static final int QUESTION_COUNT = 3;
    private static final int OPTION_COUNT = 4;

    private final AdminQuizMapper adminQuizMapper;
    private final QuizMapper quizMapper;
    private final Clock clock;

    public AdminQuizService(AdminQuizMapper adminQuizMapper, QuizMapper quizMapper, Clock clock) {
        this.adminQuizMapper = adminQuizMapper;
        this.quizMapper = quizMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public QuizPage getQuizzes(int requestedPage, int requestedSize) {
        int page = PageResponse.page(requestedPage);
        int size = PageResponse.size(requestedSize);
        long total = adminQuizMapper.countQuizzes();
        List<QuizSummary> quizzes = new ArrayList<>();
        List<AdminQuizData.QuizRow> rows = adminQuizMapper.findQuizzes(page * size, size);
        for (AdminQuizData.QuizRow row : rows) {
            quizzes.add(QuizSummary.from(row));
        }
        return new QuizPage(PageResponse.of(quizzes, page, size, total),
                adminQuizMapper.countPendingQuizzes());
    }

    @Transactional(readOnly = true)
    public QuizDetail getQuiz(long quizSetId) {
        AdminQuizData.QuizRow quiz = requireQuiz(quizSetId);
        List<QuizData.PassageRow> passageRows = quizMapper.findPassages(quizSetId);
        List<QuizData.QuestionRow> questionRows = quizMapper.findQuestions(quizSetId);
        List<QuizData.OptionRow> optionRows = quizMapper.findOptions(quizSetId);
        Map<Long, QuizData.AnswerKeyRow> keys = new HashMap<>();
        for (QuizData.AnswerKeyRow key : quizMapper.findAnswerKeys(quizSetId)) {
            keys.put(key.questionId(), key);
        }

        List<PassageDetail> passages = new ArrayList<>();
        for (QuizData.PassageRow passage : passageRows) {
            passages.add(toPassageDetail(passage, questionRows, optionRows, keys));
        }
        return new QuizDetail(QuizSummary.from(quiz), passages);
    }

    private PassageDetail toPassageDetail(
            QuizData.PassageRow passage,
            List<QuizData.QuestionRow> questionRows,
            List<QuizData.OptionRow> optionRows,
            Map<Long, QuizData.AnswerKeyRow> keys
    ) {
        List<QuestionDetail> questions = new ArrayList<>();
        for (QuizData.QuestionRow question : questionRows) {
            if (question.passageId() == passage.passageId()) {
                questions.add(toQuestionDetail(question, optionRows, keys));
            }
        }
        return new PassageDetail(
                passage.passageId(),
                passage.position(),
                passage.title(),
                passage.topic(),
                passage.content(),
                questions
        );
    }

    private QuestionDetail toQuestionDetail(
            QuizData.QuestionRow question,
            List<QuizData.OptionRow> optionRows,
            Map<Long, QuizData.AnswerKeyRow> keys
    ) {
        List<OptionDetail> options = new ArrayList<>();
        for (QuizData.OptionRow option : optionRows) {
            if (option.questionId() == question.questionId()) {
                options.add(new OptionDetail(
                        option.optionId(), option.position(), option.content()));
            }
        }

        QuizData.AnswerKeyRow key = keys.get(question.questionId());
        int correctPosition = findCorrectPosition(options, key.correctOptionId());
        return new QuestionDetail(
                question.questionId(),
                question.position(),
                question.content(),
                options,
                correctPosition,
                key.explanation(),
                key.evidence()
        );
    }

    private int findCorrectPosition(List<OptionDetail> options, long correctOptionId) {
        for (OptionDetail option : options) {
            if (option.optionId() == correctOptionId) {
                return option.position();
            }
        }
        throw new IllegalStateException("The correct option was not found.");
    }

    @Transactional
    public QuizDetail createDraft(CreateQuiz command) {
        validate(command);
        AdminQuizData.QuizInsert quiz = new AdminQuizData.QuizInsert(
                command.challengeDate(), nextVariantCode(command.challengeDate()));
        adminQuizMapper.insertQuiz(quiz);
        writeContent(quiz.getId(), command);
        return getQuiz(quiz.getId());
    }

    @Transactional
    public QuizDetail createReviewedDraft(CreateQuiz command, String aiProvider,
                                          String aiModel, String promptVersion,
                                          long generationPromptId, long validationPromptId) {
        validate(command);
        AdminQuizData.QuizInsert quiz = new AdminQuizData.QuizInsert(
                command.challengeDate(), nextVariantCode(command.challengeDate()));
        adminQuizMapper.insertQuiz(quiz);
        writeContent(quiz.getId(), command);
        if (adminQuizMapper.markReviewed(quiz.getId(), aiProvider, aiModel, promptVersion,
                generationPromptId, validationPromptId) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "QUIZ_CANNOT_BE_REVIEWED",
                    "The generated quiz could not be marked as reviewed.");
        }
        return getQuiz(quiz.getId());
    }

    @Transactional(readOnly = true)
    public int countActiveQuizSets(LocalDate challengeDate) {
        return adminQuizMapper.countActiveByDate(challengeDate);
    }

    @Transactional
    public boolean recycleUnusedPublishedQuiz(LocalDate targetDate) {
        String variantCode = nextVariantCode(targetDate);
        return adminQuizMapper.rescheduleOldestUnassignedPublishedQuiz(
                LocalDate.now(clock), targetDate, variantCode) == 1;
    }

    @Transactional
    public QuizDetail updateDraft(long quizSetId, CreateQuiz command) {
        validate(command);
        AdminQuizData.QuizRow quiz = requireEditable(quizSetId);
        if ("REVIEWED".equals(quiz.status())) {
            adminQuizMapper.invalidateGeneration(quizSetId, clock.instant());
        }
        adminQuizMapper.deleteKeys(quizSetId);
        adminQuizMapper.deleteOptions(quizSetId);
        adminQuizMapper.deleteQuestions(quizSetId);
        adminQuizMapper.deletePassages(quizSetId);
        String variantCode = quiz.challengeDate().equals(command.challengeDate())
                ? quiz.variantCode()
                : nextVariantCode(command.challengeDate());
        adminQuizMapper.updateDraftDate(quizSetId, command.challengeDate(), variantCode);
        writeContent(quizSetId, command);
        return getQuiz(quizSetId);
    }

    @Transactional
    public void deleteDraft(long quizSetId) {
        AdminQuizData.QuizRow quiz = requireEditable(quizSetId);
        if ("REVIEWED".equals(quiz.status())) {
            adminQuizMapper.invalidateGeneration(quizSetId, clock.instant());
        }
        adminQuizMapper.deleteKeys(quizSetId);
        adminQuizMapper.deleteOptions(quizSetId);
        adminQuizMapper.deleteQuestions(quizSetId);
        adminQuizMapper.deletePassages(quizSetId);
        if (adminQuizMapper.deleteDraft(quizSetId) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "QUIZ_CANNOT_BE_DELETED",
                    "Only a draft quiz can be deleted.");
        }
    }

    private void writeContent(long quizSetId, CreateQuiz command) {
        for (int passageIndex = 0; passageIndex < command.passages().size(); passageIndex++) {
            CreatePassage sourcePassage = command.passages().get(passageIndex);
            AdminQuizData.PassageInsert passage = new AdminQuizData.PassageInsert(
                    quizSetId, passageIndex + 1, clean(sourcePassage.title()),
                    sourcePassage.content().trim(), clean(sourcePassage.topic())
            );
            adminQuizMapper.insertPassage(passage);
            for (int questionIndex = 0; questionIndex < sourcePassage.questions().size(); questionIndex++) {
                CreateQuestion sourceQuestion = sourcePassage.questions().get(questionIndex);
                AdminQuizData.QuestionInsert question = new AdminQuizData.QuestionInsert(
                        passage.getId(), questionIndex + 1, sourceQuestion.content().trim()
                );
                adminQuizMapper.insertQuestion(question);
                List<AdminQuizData.OptionInsert> options = new ArrayList<>();
                for (int optionIndex = 0; optionIndex < sourceQuestion.options().size(); optionIndex++) {
                    AdminQuizData.OptionInsert option = new AdminQuizData.OptionInsert(
                            question.getId(), optionIndex + 1,
                            sourceQuestion.options().get(optionIndex).trim()
                    );
                    adminQuizMapper.insertOption(option);
                    options.add(option);
                }
                long correctOptionId = options.get(sourceQuestion.correctOptionPosition() - 1).getId();
                adminQuizMapper.insertKey(question.getId(), correctOptionId,
                        sourceQuestion.explanation().trim(), clean(sourceQuestion.evidence()));
            }
        }
    }

    @Transactional
    public QuizDetail publish(long quizSetId) {
        AdminQuizData.QuizRow quiz = requireQuiz(quizSetId);
        if ("PUBLISHED".equals(quiz.status())) return getQuiz(quizSetId);
        if (adminQuizMapper.publish(quizSetId, clock.instant()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "QUIZ_CANNOT_BE_PUBLISHED",
                    "Only a draft or reviewed quiz can be published.");
        }
        return getQuiz(quizSetId);
    }

    private void validate(CreateQuiz command) {
        if (command.challengeDate() == null || command.passages() == null
                || command.passages().size() != PASSAGE_COUNT) invalidContent();
        for (CreatePassage passage : command.passages()) {
            if (blank(passage.content()) || passage.questions() == null
                    || passage.questions().size() != QUESTION_COUNT) invalidContent();
            for (CreateQuestion question : passage.questions()) {
                if (blank(question.content()) || question.options() == null
                        || question.options().size() != OPTION_COUNT
                        || hasBlankOption(question.options())
                        || question.correctOptionPosition() < 1
                        || question.correctOptionPosition() > OPTION_COUNT
                        || blank(question.explanation())) invalidContent();
            }
        }
    }

    private boolean hasBlankOption(List<String> options) {
        for (String option : options) {
            if (blank(option)) {
                return true;
            }
        }
        return false;
    }

    private String nextVariantCode(LocalDate challengeDate) {
        Set<String> activeCodes = new HashSet<>(
                adminQuizMapper.findActiveVariantCodesByDate(challengeDate));
        for (char code = 'A'; code <= 'Z'; code++) {
            String candidate = String.valueOf(code);
            if (!activeCodes.contains(candidate)) return candidate;
        }
        throw new ApiException(HttpStatus.CONFLICT, "QUIZ_VARIANTS_EXHAUSTED",
                "No more quiz variants can be created for this date.");
    }

    private AdminQuizData.QuizRow requireQuiz(long quizSetId) {
        AdminQuizData.QuizRow row = adminQuizMapper.findQuiz(quizSetId);
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND, "QUIZ_NOT_FOUND", "The quiz was not found.");
        return row;
    }
    private AdminQuizData.QuizRow requireEditable(long quizSetId) {
        AdminQuizData.QuizRow row = requireQuiz(quizSetId);
        if (!"DRAFT".equals(row.status()) && !"REVIEWED".equals(row.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "QUIZ_DRAFT_REQUIRED",
                    "Only a draft or reviewed quiz can be changed.");
        }
        return row;
    }
    private void invalidContent() { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUIZ_CONTENT", "A quiz requires 3 passages, 3 questions per passage, and 4 options per question."); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String clean(String value) { return blank(value) ? null : value.trim(); }

    public record CreateQuiz(LocalDate challengeDate, List<CreatePassage> passages) {}
    public record CreatePassage(String title, String topic, String content, List<CreateQuestion> questions) {}
    public record CreateQuestion(String content, List<String> options, int correctOptionPosition, String explanation, String evidence) {}
    public record QuizSummary(long quizSetId, LocalDate challengeDate, String variantCode,
                              String status, java.time.Instant createdAt,
                              java.time.Instant publishedAt) {
        static QuizSummary from(AdminQuizData.QuizRow row) {
            return new QuizSummary(row.quizSetId(), row.challengeDate(), row.variantCode(),
                    row.status(), row.createdAt(), row.publishedAt());
        }
    }
    public record QuizPage(PageResponse<QuizSummary> page, long pendingCount) {}
    public record QuizDetail(QuizSummary quiz, List<PassageDetail> passages) {}
    public record PassageDetail(long passageId, int position, String title, String topic, String content, List<QuestionDetail> questions) {}
    public record QuestionDetail(long questionId, int position, String content, List<OptionDetail> options, int correctOptionPosition, String explanation, String evidence) {}
    public record OptionDetail(long optionId, int position, String content) {}
}

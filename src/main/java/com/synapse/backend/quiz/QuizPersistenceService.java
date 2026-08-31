package com.synapse.backend.quiz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.synapse.backend.groups.repositories.StudyGroupRepository;
import com.synapse.backend.quiz.dto.AnswerResponse;
import com.synapse.backend.quiz.dto.QuestionResponse;
import com.synapse.backend.quiz.dto.QuizResponse;
import com.synapse.backend.quiz.dto.create.CreateAnswerResponse;
import com.synapse.backend.quiz.dto.create.CreateQuestionAnswer;
import com.synapse.backend.quiz.dto.create.CreateQuestionRequest;
import com.synapse.backend.quiz.dto.create.CreateQuestionResponse;
import com.synapse.backend.quiz.dto.generated.GeneratedAnswerResponse;
import com.synapse.backend.quiz.dto.generated.GeneratedQuestionResponse;
import com.synapse.backend.quiz.dto.generated.GeneratedQuizResponse;
import com.synapse.backend.quiz.dto.list.ListQuizResponse;
import com.synapse.backend.quiz.dto.list.QuestionPreviewResponse;
import com.synapse.backend.quiz.dto.list.QuizListItemResponse;
import com.synapse.backend.quiz.dto.score.ListQuizScoreResponse;
import com.synapse.backend.quiz.dto.score.QuizScoreResponse;
import com.synapse.backend.quiz.entities.Quiz;
import com.synapse.backend.quiz.entities.QuizAnswer;
import com.synapse.backend.quiz.entities.QuizQuestion;
import com.synapse.backend.quiz.entities.QuizScore;
import com.synapse.backend.quiz.enums.QuestionType;
import com.synapse.backend.quiz.enums.QuizSourceType;
import com.synapse.backend.quiz.exceptions.CreateQuestionInputException;
import com.synapse.backend.quiz.exceptions.InvalidQuizScoreException;
import com.synapse.backend.quiz.exceptions.QuestionNotFound;
import com.synapse.backend.quiz.exceptions.QuizNotFound;
import com.synapse.backend.quiz.repositories.QuizAnswerRepository;
import com.synapse.backend.quiz.repositories.QuizQuestionRepository;
import com.synapse.backend.quiz.repositories.QuizRepository;
import com.synapse.backend.quiz.repositories.QuizScoreRepository;
import com.synapse.backend.shared.exceptions.concrete.UserUnauthorised;

import jakarta.transaction.Transactional;

@Service
public class QuizPersistenceService {
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizAnswerRepository answerRepository;
    private final QuizScoreRepository scoreRepository;
    private final StudyGroupRepository studyGroupRepository;

    public QuizPersistenceService(
        QuizRepository quizRepository,
        QuizQuestionRepository questionRepository,
        QuizAnswerRepository answerRepository,
        QuizScoreRepository scoreRepository,
        StudyGroupRepository studyGroupRepository
    ) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.scoreRepository = scoreRepository;
        this.studyGroupRepository = studyGroupRepository;
    }

    /**
     * Saves a generated note-based quiz, including all questions and answers.
     *
     * @param generatedQuiz generated quiz data returned by the LLM.
     * @param userId the id of the authenticated user who owns the quiz.
     * @param noteId the internal id of the source note.
     * @return the saved quiz response with public ids and timestamps.
     */
    @Transactional
    public QuizResponse saveQuizFromNote(GeneratedQuizResponse generatedQuiz, Long userId, Long noteId) {
        Quiz quiz = new Quiz(generatedQuiz.title(), generatedQuiz.description(), userId, noteId, QuizSourceType.NOTE);
        Quiz newQuiz = quizRepository.save(quiz);

        List<GeneratedQuestionResponse> generatedQuestions = generatedQuiz.questions();
        List<QuestionResponse> questions = new ArrayList<>();

        for (int i = 0; i < generatedQuestions.size(); i++) {
            GeneratedQuestionResponse question = generatedQuestions.get(i);
            QuizQuestion quizQuestion = saveQuestion(question, newQuiz.getId(), i);

            List<QuizAnswer> answers = saveAnswers(question.answers(), quizQuestion.getId());

            List<AnswerResponse> answerResponses = answers.stream()
                .map(a -> new AnswerResponse(
                    a.getPublicId(),
                    a.getAnswerText(),
                    a.isCorrect(),
                    a.getCreatedAt()
                ))
                .toList();

            questions.add(
                new QuestionResponse(
                    quizQuestion.getPublicId(),
                    quizQuestion.getQuestionText(),
                    quizQuestion.getQuestionType(),
                    answerResponses,
                    quizQuestion.getCreatedAt()
                )
            );
        }

        return new QuizResponse(
            newQuiz.getPublicId(),
            newQuiz.getTitle(),
            newQuiz.getDescription(),
            questions,
            newQuiz.getDifficulty(),
            newQuiz.getCreatedAt(),
            null
        );
    }

    /**
     * Persists a generated question for a quiz at the given position.
     *
     * @param generatedQuestion generated question data.
     * @param quizId internal id of the parent quiz.
     * @param position zero-based question position within the quiz.
     * @return the saved question entity.
     */
    private QuizQuestion saveQuestion(GeneratedQuestionResponse generatedQuestion, Long quizId, int position) {
        String questionText = generatedQuestion.questionText();
        QuestionType questionType = generatedQuestion.questionType();
        QuizQuestion question = new QuizQuestion(quizId, questionText, questionType, position);

        return questionRepository.save(question);
    }

    /**
     * Persists generated answers for a question, preserving their response order as position.
     *
     * @param generatedAnswers generated answer options.
     * @param questionId internal id of the parent question.
     * @return the saved answer entities.
     */
    private List<QuizAnswer> saveAnswers(List<GeneratedAnswerResponse> generatedAnswers, Long questionId) {
        List<QuizAnswer> answers = new ArrayList<>();

        for (int i = 0; i < generatedAnswers.size(); i++) {
            String answerText = generatedAnswers.get(i).answerText();
            boolean isCorrect = generatedAnswers.get(i).correct();

            answers.add(new QuizAnswer(questionId, answerText, isCorrect, i));
        }

        return answerRepository.saveAll(answers);
    }

    /**
     * Returns all quizzes owned by a user with question previews.
     *
     * @param userId the id of the authenticated user.
     * @return saved quizzes with question previews, excluding answer options.
     */
    public ListQuizResponse getAllQuizzes(Long userId) {
        List<Quiz> quizzes = quizRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Long> quizIds = quizzes.stream().map(Quiz::getId).toList();

        if (quizIds.isEmpty())
            return new ListQuizResponse(List.of());

        Map<Long, List<QuizQuestion>> quizQuestions = questionRepository
            .findByQuizIdInOrderByQuizIdAscPositionAsc(quizIds)
            .stream()
            .collect(Collectors.groupingBy(QuizQuestion::getQuizId));

        List<QuizListItemResponse> quizResponses = new ArrayList<>();

        for (Quiz quiz : quizzes) {
            List<QuestionPreviewResponse> questionResponses = quizQuestions
                .getOrDefault(quiz.getId(), List.of())
                .stream()
                .map(q -> new QuestionPreviewResponse(q.getPublicId(), q.getQuestionText(), q.getCreatedAt()))
                .toList();

            quizResponses.add(
                new QuizListItemResponse(
                    quiz.getPublicId(),
                    quiz.getTitle(),
                    quiz.getDescription(),
                    questionResponses,
                    quiz.getDifficulty(),
                    quiz.getCreatedAt(),
                    groupPublicId(quiz.getGroupId())
                )
            );
        }

        return new ListQuizResponse(quizResponses);
    }

    /**
     * Returns a single quiz owned by a user.
     *
     * @param quizId the public id of the quiz.
     * @param userId the id of the authenticated user.
     * @return the quiz with ordered questions and answers.
     * @throws QuizNotFound if no quiz with the given public id belongs to the user.
     */
    public QuizResponse getQuizById(String quizId, Long userId) {
        if (userId == null)
            throw new UserUnauthorised("User is not authenticated for this action.");

        Quiz quiz = quizRepository
            .findByPublicIdAndUserId(quizId, userId)
            .orElseThrow(() -> new QuizNotFound("Quiz not found: " + quizId));

        return toQuizResponse(quiz);
    }

    /**
     * Maps a quiz to its full response with ordered questions and answers.
     *
     * @param quiz the quiz to map.
     * @return the quiz with ordered questions and answers.
     */
    private QuizResponse toQuizResponse(Quiz quiz) {
        List<QuizQuestion> quizQuestions = questionRepository.findByQuizIdOrderByPositionAsc(quiz.getId());

        List<Long> questionIds = quizQuestions.stream().map(QuizQuestion::getId).toList();

        Map<Long, List<QuizAnswer>> answers = answerRepository
            .findByQuestionIdInOrderByQuestionIdAscPositionAsc(questionIds)
            .stream()
            .collect(Collectors.groupingBy(QuizAnswer::getQuestionId));

        List<QuestionResponse> questions = new ArrayList<>();

        for (QuizQuestion q : quizQuestions) {
            List<AnswerResponse> quizAnswers = answers
                .getOrDefault(q.getId(), List.of())
                .stream().map(a -> new AnswerResponse(a.getPublicId(), a.getAnswerText(), a.isCorrect(), a.getCreatedAt()))
                .toList();

            questions.add(
                new QuestionResponse(
                    q.getPublicId(),
                    q.getQuestionText(),
                    q.getQuestionType(),
                    quizAnswers,
                    q.getCreatedAt()
                )
            );

        }

        return new QuizResponse(
            quiz.getPublicId(),
            quiz.getTitle(),
            quiz.getDescription(),
            questions,
            quiz.getDifficulty(),
            quiz.getCreatedAt(),
            groupPublicId(quiz.getGroupId())
        );
    }

    /**
     * Resolves the public id of the study group a quiz belongs to.
     *
     * @param groupId the internal group id held by the quiz, or null when it is ungrouped.
     * @return the group's public id, or null when the quiz is not in a group.
     */
    private String groupPublicId(Long groupId) {
        if (groupId == null)
            return null;

        return studyGroupRepository.findPublicIdById(groupId).orElse(null);
    }

    /**
     * Deletes a quiz owned by a user.
     *
     * @param quizId the public id of the quiz.
     * @param userId the id of the authenticated user.
     * @throws QuizNotFound if no quiz with the given public id belongs to the user.
     */
    @Transactional
    public void deleteQuizById(String quizId, Long userId) {
        if (userId == null)
            throw new UserUnauthorised("User is not authenticated for this action.");

        long isDeleted = quizRepository.deleteByPublicIdAndUserId(quizId, userId);

        if (isDeleted == 0)
            throw new QuizNotFound("Quiz not found: " + quizId);
    }

    /**
     * Updates the supplied fields of a quiz owned by the user.
     *
     * <p>A blank description is stored as null, following the group description pattern.</p>
     *
     * @param userId the id of the authenticated user.
     * @param quizId the public id of the quiz to update.
     * @param title the new title, or null to leave it unchanged.
     * @param description the new description, or null to leave it unchanged.
     * @return the updated quiz with ordered questions and answers.
     * @throws QuizNotFound if no quiz with the given public id belongs to the user.
     */
    @Transactional
    public QuizResponse updateQuiz(Long userId, String quizId, String title, String description) {
        Quiz quiz = quizRepository
            .findByPublicIdAndUserId(quizId, userId)
            .orElseThrow(() -> new QuizNotFound("Quiz not found: " + quizId));

        if (title != null)
            quiz.updateTitle(title);

        if (description != null)
            quiz.updateDescription(description.isBlank() ? null : description);

        quizRepository.save(quiz);

        return toQuizResponse(quiz);
    }

    /**
     * Persists a manually-created question and answer set for a quiz owned by the user.
     *
     * @param userId the id of the authenticated user.
     * @param quizId the public id of the quiz to add the question to.
     * @param req the validated question and answer data.
     * @return the created question response with generated public ids.
     * @throws QuizNotFound if no quiz with the given public id belongs to the user.
     */
    @Transactional
    public CreateQuestionResponse createQuestion(Long userId, String quizId, CreateQuestionRequest req) {
        Quiz quiz = quizRepository
            .findByPublicIdAndUserId(quizId, userId)
            .orElseThrow(() -> new QuizNotFound("Quiz not found: " + quizId));

        Integer maxPosition = questionRepository.findMaxPositionByQuizId(quiz.getId()).orElse(-1);

        QuizQuestion question = new QuizQuestion(quiz.getId(), req.question(), req.questionType(), maxPosition + 1);

        QuizQuestion newQuestion = questionRepository.save(question);

        List<QuizAnswer> answers = new ArrayList<>();
        for (int i = 0; i < req.answers().size(); i++) {
            CreateQuestionAnswer answer = req.answers().get(i);

            answers.add(new QuizAnswer(newQuestion.getId(), answer.answer(), answer.isCorrect(), i));
        }

        List<QuizAnswer> newAnswers = answerRepository.saveAll(answers);

        quizRepository.updateUpdatedAtById(quiz.getId());

        List<CreateAnswerResponse> answerResponses = newAnswers.
            stream()
            .map(a -> new CreateAnswerResponse(a.getPublicId(), a.getAnswerText(), a.isCorrect()))
            .toList();

        return new CreateQuestionResponse(
            newQuestion.getPublicId(),
            newQuestion.getQuestionText(),
            newQuestion.getQuestionType(),
            answerResponses,
            newQuestion.getCreatedAt()
        );
    }

    /**
     * Deletes a question from a quiz owned by the user and updates the quiz modified timestamp.
     *
     * @param userId the id of the authenticated user.
     * @param quizId the public id of the quiz containing the question.
     * @param questionId the public id of the question to delete.
     * @throws QuizNotFound if no quiz with the given public id belongs to the user.
     * @throws QuestionNotFound if the question does not exist in the quiz.
     */
    @Transactional
    public void deleteQuestion(Long userId, String quizId, String questionId) {
        Quiz quiz = quizRepository
            .findByPublicIdAndUserId(quizId, userId)
            .orElseThrow(() -> new QuizNotFound("Quiz not found: " + quizId));

        long isDeleted = questionRepository.deleteByPublicIdAndQuizId(questionId, quiz.getId());

        if (isDeleted == 0)
            throw new QuestionNotFound("Question not found: " + questionId);

        quizRepository.updateUpdatedAtById(quiz.getId());
    }

    /**
     * Updates the supplied fields of a question in a quiz owned by the user.
     *
     * <p>When {@code answers} is supplied it replaces the question's whole answer set. The
     * resulting question is validated against the same rules as manual question creation:
     * 4 answers for multiple choice, 2 for boolean, and exactly one correct answer. The
     * question and answer changes commit together, and the parent quiz modified timestamp
     * is advanced, matching manual question creation and deletion.</p>
     *
     * <p>The question is loaded with a pessimistic write lock before its answers are read, so
     * concurrent updates of the same question run one after the other and the second update
     * replaces the answer set the first one saved rather than a stale one. Ordinary question
     * retrieval stays unlocked.</p>
     *
     * @param userId the id of the authenticated user.
     * @param quizId the public id of the quiz containing the question.
     * @param questionId the public id of the question to update.
     * @param questionText the new question text, or null to leave it unchanged.
     * @param questionType the new question type, or null to leave it unchanged.
     * @param answers the complete replacement answer set, or null to leave the answers unchanged.
     * @return the updated question with its answers.
     * @throws QuizNotFound if no quiz with the given public id belongs to the user.
     * @throws QuestionNotFound if the question does not exist in the quiz.
     * @throws CreateQuestionInputException if the resulting question breaks the answer count or
     *     correct-answer rules.
     */
    @Transactional
    public CreateQuestionResponse updateQuestion(
        Long userId,
        String quizId,
        String questionId,
        String questionText,
        QuestionType questionType,
        List<CreateQuestionAnswer> answers
    ) {
        Quiz quiz = quizRepository
            .findByPublicIdAndUserId(quizId, userId)
            .orElseThrow(() -> new QuizNotFound("Quiz not found: " + quizId));

        QuizQuestion question = questionRepository
            .findByPublicIdAndQuizIdForUpdate(questionId, quiz.getId())
            .orElseThrow(() -> new QuestionNotFound("Question not found: " + questionId));

        List<QuizAnswer> currentAnswers = answerRepository.findByQuestionIdOrderByPositionAsc(question.getId());

        QuestionType resultingType = questionType != null ? questionType : question.getQuestionType();
        int resultingAnswerCount = answers != null ? answers.size() : currentAnswers.size();
        long resultingCorrectCount = answers != null
            ? answers.stream().filter(CreateQuestionAnswer::isCorrect).count()
            : currentAnswers.stream().filter(QuizAnswer::isCorrect).count();

        if (!isValidQuestionShape(resultingType, resultingAnswerCount, resultingCorrectCount)) {
            throw new CreateQuestionInputException(
                """
                Request data is invalid. Only one correct answer is allowed.
                Multiple choice questions must have 4 answers and boolean questions must have 2.
                """
            );
        }

        if (questionText != null)
            question.updateQuestionText(questionText);

        if (questionType != null)
            question.updateQuestionType(questionType);

        questionRepository.save(question);

        List<QuizAnswer> resultingAnswers = currentAnswers;

        if (answers != null) {
            answerRepository.deleteAll(currentAnswers);
            answerRepository.flush();

            List<QuizAnswer> replacements = new ArrayList<>();
            for (int i = 0; i < answers.size(); i++) {
                CreateQuestionAnswer answer = answers.get(i);

                replacements.add(new QuizAnswer(question.getId(), answer.answer(), answer.isCorrect(), i));
            }

            resultingAnswers = answerRepository.saveAll(replacements);
        }

        quizRepository.updateUpdatedAtById(quiz.getId());

        List<CreateAnswerResponse> answerResponses = resultingAnswers
            .stream()
            .map(a -> new CreateAnswerResponse(a.getPublicId(), a.getAnswerText(), a.isCorrect()))
            .toList();

        return new CreateQuestionResponse(
            question.getPublicId(),
            question.getQuestionText(),
            question.getQuestionType(),
            answerResponses,
            question.getCreatedAt()
        );
    }

    /**
     * Checks type-specific answer count and correct-answer rules for a question.
     *
     * @param questionType the resulting question type.
     * @param answerCount the resulting number of answers.
     * @param correctCount the resulting number of correct answers.
     * @return true when the answer shape is valid for the question type.
     */
    private boolean isValidQuestionShape(QuestionType questionType, int answerCount, long correctCount) {
        int requiredCount = questionType == QuestionType.MULTIPLE_CHOICE ? 4 : 2;

        return answerCount == requiredCount && correctCount == 1;
    }

    /**
     * Persists a new difficulty for a quiz owned by the user.
     *
     * @param userId the id of the authenticated user.
     * @param quizId the public id of the quiz to update.
     * @param difficulty the validated difficulty from 1 to 5.
     * @throws QuizNotFound if no quiz with the given public id belongs to the user.
     */
    @Transactional
    public void updateDifficulty(Long userId, String quizId, Integer difficulty) {
        Quiz quiz = quizRepository
            .findByPublicIdAndUserId(quizId, userId)
            .orElseThrow(() -> new QuizNotFound("Quiz not found: " + quizId));

        quizRepository.updateDifficultyById(quiz.getId(), difficulty);
    }

    /**
     * Persists a score and question-count snapshot for a quiz owned by the user.
     *
     * @param quizId the public id of the completed quiz.
     * @param userId the id of the authenticated user.
     * @param score the validated non-negative score to save.
     * @return the saved score with its quiz id, question-count snapshot, and creation time.
     * @throws QuizNotFound if no quiz with the given public id belongs to the user.
     * @throws InvalidQuizScoreException if the score exceeds the number of quiz questions.
     */
    public QuizScoreResponse saveScore(String quizId, Long userId, int score) {
        Quiz quiz = quizRepository
            .findByPublicIdAndUserId(quizId, userId)
            .orElseThrow(() -> new QuizNotFound("Quiz not found: " + quizId));

        int numQuestions = questionRepository.countByQuizId(quiz.getId());

        if (score > numQuestions)
            throw new InvalidQuizScoreException("Score cannot be greater than number of questions.");

        QuizScore quizScore = scoreRepository.save(new QuizScore(quiz.getId(), userId, score, numQuestions));

        return new QuizScoreResponse(
            quizScore.getPublicId(),
            quiz.getPublicId(),
            quizScore.getScore(),
            quizScore.getTotalQuestions(),
            quizScore.getCreatedAt()
        );
    }

    /**
     * Retrieves score history for a quiz owned by the user.
     *
     * @param quizId the public id of the quiz.
     * @param userId the id of the authenticated user.
     * @return saved score attempts ordered from newest to oldest with their question-count snapshots.
     * @throws QuizNotFound if no quiz with the given public id belongs to the user.
     */
    public ListQuizScoreResponse getAllQuizScores(String quizId, Long userId) {
        Quiz quiz = quizRepository
            .findByPublicIdAndUserId(quizId, userId)
            .orElseThrow(() -> new QuizNotFound("Quiz not found: " + quizId));

        List<QuizScore> scores = scoreRepository.findByQuizIdOrderByCreatedAtDesc(quiz.getId());

        return new ListQuizScoreResponse(
            scores
                .stream()
                .map(s -> new QuizScoreResponse(
                    s.getPublicId(),
                    quiz.getPublicId(),
                    s.getScore(),
                    s.getTotalQuestions(),
                    s.getCreatedAt())
                )
                .toList()
        );
    }

}

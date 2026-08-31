package com.synapse.backend.quiz;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.ai.exceptions.LLMResponseParsingException;
import com.synapse.backend.ai.prompts.QuizGeneratePromptFactory;
import com.synapse.backend.notes.NotesService;
import com.synapse.backend.notes.dto.NoteForGeneration;
import com.synapse.backend.quiz.dto.QuizResponse;
import com.synapse.backend.quiz.dto.UpdateQuestionRequest;
import com.synapse.backend.quiz.dto.UpdateQuizRequest;
import com.synapse.backend.quiz.dto.create.CreateQuestionAnswer;
import com.synapse.backend.quiz.dto.create.CreateQuestionRequest;
import com.synapse.backend.quiz.dto.create.CreateQuestionResponse;
import com.synapse.backend.quiz.dto.generated.GeneratedAnswerResponse;
import com.synapse.backend.quiz.dto.generated.GeneratedQuestionResponse;
import com.synapse.backend.quiz.dto.generated.GeneratedQuizResponse;
import com.synapse.backend.quiz.dto.list.ListQuizResponse;
import com.synapse.backend.quiz.dto.score.ListQuizScoreResponse;
import com.synapse.backend.quiz.dto.score.QuizScoreResponse;
import com.synapse.backend.quiz.enums.QuestionType;
import com.synapse.backend.quiz.exceptions.CreateQuestionInputException;
import com.synapse.backend.quiz.exceptions.InvalidQuizDetailsException;
import com.synapse.backend.shared.exceptions.concrete.UserUnauthorised;
import com.synapse.backend.streak.StreakService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class QuizService {
    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;
    private final NotesService notesService;
    private final QuizGeneratePromptFactory promptFactory;
    private final QuizPersistenceService persistenceService;
    private final StreakService streakService;

    public QuizService(
        LLMClient llmClient,
        ObjectMapper objectMapper,
        NotesService notesService,
        QuizGeneratePromptFactory promptFactory,
        QuizPersistenceService persistenceService,
        StreakService streakService
    ) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.notesService = notesService;
        this.promptFactory = promptFactory;
        this.persistenceService = persistenceService;
        this.streakService = streakService;
    }

    /**
     * Generates a quiz from a saved note and persists the quiz hierarchy.
     *
     * <p>Study activity is recorded once the quiz has been saved.</p>
     *
     * @param noteId the public id of the note to generate a quiz from.
     * @param userId the id of the authenticated user.
     * @return the generated quiz, including saved question and answer ids.
     * @throws LLMResponseParsingException if the LLM response cannot be parsed or violates the quiz schema.
     */
    public QuizResponse generateQuizFromNote(String noteId, Long userId) {
        NoteForGeneration note = notesService.getNoteForGeneration(noteId, userId);

        LLMRequest req = new LLMRequest(
            "openai/gpt-oss-120b",
            promptFactory.createSystemPrompt(),
            promptFactory.createUserPrompt(
                objectMapper.writeValueAsString(note.summary())
            )
        );

        String res = llmClient.generate(req);
        try {
            GeneratedQuizResponse generatedQuiz = objectMapper.readValue(res, GeneratedQuizResponse.class);
            validateQuizStructure(generatedQuiz);

            QuizResponse savedQuiz = persistenceService.saveQuizFromNote(generatedQuiz, userId, note.id());
            streakService.recordActivity(userId);

            return savedQuiz;
        } catch (JacksonException e) {
            throw new LLMResponseParsingException("Failed to parse LLM response");
        }
    }

    /**
     * Verifies that generated quiz data has the structure required before persistence.
     *
     * @param generatedQuiz parsed LLM quiz response.
     * @throws LLMResponseParsingException if the generated quiz does not match the expected quiz shape.
     */
    private void validateQuizStructure(GeneratedQuizResponse generatedQuiz) {
        if (generatedQuiz.questions() == null || generatedQuiz.questions().size() != 10)
            throw new LLMResponseParsingException("Failed to parse LLM response");

        for (GeneratedQuestionResponse question : generatedQuiz.questions()) {
            List<GeneratedAnswerResponse> answers = question.answers();

            if (question.questionType() == QuestionType.MULTIPLE_CHOICE && answers.size() != 4) {
                throw new LLMResponseParsingException("Failed to parse LLM response");
            } else if (question.questionType() == QuestionType.BOOLEAN && answers.size() != 2) {
                throw new LLMResponseParsingException("Failed to parse LLM response");
            }
        }
    }

    /**
     * Returns a page of quizzes owned by a user with question previews, optionally filtered by title.
     *
     * @param userId the id of the authenticated user.
     * @param query an optional case-insensitive partial title search, or null/blank for no search.
     * @param pageable the page to return.
     * @return the requested page of quizzes with question previews, excluding answer options.
     */
    public ListQuizResponse getAllQuizzes(Long userId, String query, Pageable pageable) {
        return persistenceService.getAllQuizzes(userId, query, pageable);
    }

    /**
     * Returns a full quiz owned by a user.
     *
     * @param quizId the public id of the quiz.
     * @param userId the id of the authenticated user.
     * @return the quiz with ordered questions and answers.
     */
    public QuizResponse getQuizById(String quizId, Long userId) {
        return persistenceService.getQuizById(quizId, userId);
    }

    /**
     * Deletes a quiz owned by a user.
     *
     * @param quizId the public id of the quiz.
     * @param userId the id of the authenticated user.
     */
    public void deleteQuizById(String quizId, Long userId) {
        persistenceService.deleteQuizById(quizId, userId);
    }

    /**
     * Validates and creates a new question with answers for a quiz owned by the user.
     *
     * @param userId the id of the authenticated user.
     * @param quizId the public id of the quiz to add the question to.
     * @param req the question and answer data to create.
     * @return the created question with public ids for the question and answers.
     */
    public CreateQuestionResponse createQuestion(Long userId, String quizId, CreateQuestionRequest req) {
        if (userId == null)
            throw new UserUnauthorised("User is not authenticated.");

        if (!validateCreateQuestionInput(req.questionType(), req.answers())) {
            throw new CreateQuestionInputException(
                """
                Request data is invalid. Only one correct answer is allowed.
                Multiple choice questions must have 4 answers and boolean questions must have 2.
                """
            );
        }

        return persistenceService.createQuestion(userId, quizId, req);
    }

    /**
     * Checks question-type-specific answer count and correct-answer rules.
     *
     * @param questionType the type of question being created.
     * @param answers the submitted answer options.
     * @return true when the answer shape is valid for the question type.
     */
    private boolean validateCreateQuestionInput(QuestionType questionType, List<CreateQuestionAnswer> answers) {
        int correctAnswersLen = (questionType == QuestionType.MULTIPLE_CHOICE) ? 4 : 2;
        int answersLen = answers.size();
        boolean correctAnswerExists = answers.stream().filter(a -> a.isCorrect()).count() == 1;

        return (answersLen == correctAnswersLen) && correctAnswerExists;
    }

    /**
     * Updates the title and/or description of a quiz owned by the authenticated user.
     *
     * <p>Only the supplied fields are changed. The request arrives with its title and description
     * trimmed. A blank description clears it. The dedicated difficulty endpoint is unaffected.</p>
     *
     * @param userId the id of the authenticated user.
     * @param quizId the public id of the quiz to update.
     * @param req the validated fields to update, with at least one field supplied.
     * @return the updated quiz with ordered questions and answers.
     * @throws InvalidQuizDetailsException if no field is supplied or the supplied title is blank.
     */
    public QuizResponse updateQuiz(Long userId, String quizId, UpdateQuizRequest req) {
        if (userId == null)
            throw new UserUnauthorised("User is not authorised for this action.");

        String title = req.title();
        String description = req.description();

        if (title == null && description == null)
            throw new InvalidQuizDetailsException("At least one of title or description must be supplied.");

        if (title != null && title.isBlank())
            throw new InvalidQuizDetailsException("title: must not be blank");

        return persistenceService.updateQuiz(userId, quizId, title, description);
    }

    /**
     * Updates the supplied fields of a question in a quiz owned by the authenticated user.
     *
     * <p>Only the supplied fields are changed. When {@code answers} is supplied it replaces the
     * question's whole answer set. The resulting question is validated against the manual
     * question creation rules, and the question and answer changes commit together.</p>
     *
     * @param userId the id of the authenticated user.
     * @param quizId the public id of the quiz containing the question.
     * @param questionId the public id of the question to update.
     * @param req the validated fields to update, with at least one field supplied.
     * @return the updated question with its answers.
     * @throws CreateQuestionInputException if no field is supplied, the supplied question text is
     *     blank, or the resulting question breaks the answer count or correct-answer rules.
     */
    public CreateQuestionResponse updateQuestion(
        Long userId,
        String quizId,
        String questionId,
        UpdateQuestionRequest req
    ) {
        if (userId == null)
            throw new UserUnauthorised("User is not authorised for this action.");

        String question = req.question();
        QuestionType questionType = req.questionType();
        List<CreateQuestionAnswer> answers = req.answers();

        if (question == null && questionType == null && answers == null)
            throw new CreateQuestionInputException(
                "At least one of question, questionType, or answers must be supplied."
            );

        if (question != null && question.isBlank())
            throw new CreateQuestionInputException("question: must not be blank");

        return persistenceService.updateQuestion(userId, quizId, questionId, question, questionType, answers);
    }

    /**
     * Deletes a question from a quiz owned by the user.
     *
     * @param userId the id of the authenticated user.
     * @param quizId the public id of the quiz containing the question.
     * @param questionId the public id of the question to delete.
     */
    public void deleteQuestion(Long userId, String quizId, String questionId) {
        if (userId == null)
            throw new UserUnauthorised("User is not authorised for this action.");

        persistenceService.deleteQuestion(userId, quizId, questionId);
    }

    /**
     * Updates the difficulty of a quiz owned by the authenticated user.
     *
     * @param userId the id of the authenticated user.
     * @param quizId the public id of the quiz to update.
     * @param difficulty the validated difficulty from 1 to 5.
     */
    public void updateDifficulty(Long userId, String quizId, Integer difficulty) {
        if (userId == null)
            throw new UserUnauthorised("User is not authorised for this action.");

        persistenceService.updateDifficulty(userId, quizId, difficulty);
    }

    /**
     * Saves a completed score for a quiz owned by the authenticated user.
     *
     * <p>Study activity is recorded once the score has been saved.</p>
     *
     * @param quizId the public id of the completed quiz.
     * @param userId the id of the authenticated user.
     * @param score the number of correctly answered questions.
     * @return the saved score with its quiz id, question-count snapshot, and creation time.
     */
    public QuizScoreResponse saveScore(String quizId, Long userId, int score) {
        if (userId == null)
            throw new UserUnauthorised("User is not authorised for this action.");

        QuizScoreResponse savedScore = persistenceService.saveScore(quizId, userId, score);
        streakService.recordActivity(userId);

        return savedScore;
    }

    /**
     * Returns the score history for a quiz owned by the authenticated user.
     *
     * @param quizId the public id of the quiz.
     * @param userId the id of the authenticated user.
     * @return saved score attempts ordered from newest to oldest.
     */
    public ListQuizScoreResponse getAllQuizScores(String quizId, Long userId) {
        if (userId == null)
            throw new UserUnauthorised("User is not authorised for this action.");

        return persistenceService.getAllQuizScores(quizId, userId);
    }

}

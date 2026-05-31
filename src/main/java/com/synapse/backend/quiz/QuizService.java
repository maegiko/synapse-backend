package com.synapse.backend.quiz;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.ai.exceptions.LLMResponseParsingException;
import com.synapse.backend.ai.prompts.QuizGeneratePromptFactory;
import com.synapse.backend.notes.NotesService;
import com.synapse.backend.notes.dto.NoteForGeneration;
import com.synapse.backend.quiz.dto.QuizResponse;
import com.synapse.backend.quiz.dto.generated.GeneratedAnswerResponse;
import com.synapse.backend.quiz.dto.generated.GeneratedQuestionResponse;
import com.synapse.backend.quiz.dto.generated.GeneratedQuizResponse;
import com.synapse.backend.quiz.dto.list.ListQuizResponse;
import com.synapse.backend.quiz.enums.QuestionType;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class QuizService {
    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;
    private final NotesService notesService;
    private final QuizGeneratePromptFactory promptFactory;
    private final QuizPersistenceService persistenceService;

    public QuizService(
        LLMClient llmClient,
        ObjectMapper objectMapper,
        NotesService notesService,
        QuizGeneratePromptFactory promptFactory,
        QuizPersistenceService persistenceService
    ) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.notesService = notesService;
        this.promptFactory = promptFactory;
        this.persistenceService = persistenceService;
    }

    /**
     * Generates a quiz from a saved note and persists the quiz hierarchy.
     *
     * @param noteId the public id of the note to generate a quiz from.
     * @param userId the id of the authenticated user.
     * @return the generated quiz, including saved question and answer ids.
     * @throws LLMResponseParsingException if the LLM response cannot be parsed or violates the quiz schema.
     */
    public QuizResponse generateQuizFromNote(UUID noteId, Long userId) {
        NoteForGeneration note = notesService.getNoteForGeneration(noteId, userId);

        LLMRequest req = new LLMRequest(
            "meta-llama/llama-4-scout-17b-16e-instruct",
            promptFactory.createSystemPrompt(),
            promptFactory.createUserPrompt(
                objectMapper.writeValueAsString(note.summary())
            )
        );

        String res = llmClient.generate(req);
        try {
            GeneratedQuizResponse generatedQuiz = objectMapper.readValue(res, GeneratedQuizResponse.class);
            validateQuizStructure(generatedQuiz);

            return persistenceService.saveQuizFromNote(generatedQuiz, userId, note.id());
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
     * Returns all quizzes owned by a user with question previews.
     *
     * @param userId the id of the authenticated user.
     * @return saved quizzes with question previews, excluding answer options.
     */
    public ListQuizResponse getAllQuizzes(Long userId) {
        return persistenceService.getAllQuizzes(userId);
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

}

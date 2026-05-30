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

}

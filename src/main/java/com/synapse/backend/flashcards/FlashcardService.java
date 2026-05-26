package com.synapse.backend.flashcards;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.ai.exceptions.LLMResponseParsingException;
import com.synapse.backend.ai.prompts.FlashcardGeneratePromptFactory;
import com.synapse.backend.flashcards.dto.FlashcardGenerateListResponse;
import com.synapse.backend.flashcards.dto.FlashcardGenerateResponse;
import com.synapse.backend.notes.NotesService;
import com.synapse.backend.notes.dto.NoteSummaryResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class FlashcardService {
    private final NotesService notesService;
    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;
    private final FlashcardGeneratePromptFactory promptFactory;
    private final FlashcardPersistenceService persistenceService;

    public FlashcardService(
        NotesService notesService,
        LLMClient llmClient,
        ObjectMapper objectMapper,
        FlashcardGeneratePromptFactory promptFactory,
        FlashcardPersistenceService persistenceService
    ) {
        this.notesService = notesService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.promptFactory = promptFactory;
        this.persistenceService = persistenceService;
    }

    public FlashcardGenerateListResponse generateFlashCards(Long noteId, Long userId) {
        NoteSummaryResponse note = notesService.getNoteSummary(noteId, userId);

        List<FlashcardGenerateResponse> flashcards = new ArrayList<>(
            note.concepts()
                .stream()
                .map(c -> new FlashcardGenerateResponse(c.name(), c.explanation()))
                .toList()
        );

        LLMRequest req = new LLMRequest(
            "meta-llama/llama-4-scout-17b-16e-instruct",
            promptFactory.createFlashcardSystemPrompt(),
            promptFactory.createFlashcardUserPrompt(
                objectMapper.writeValueAsString(note),
                objectMapper.writeValueAsString(flashcards)
            )
        );

        String res = llmClient.generate(req);
        try {
            FlashcardGenerateListResponse generatedFlashcards = objectMapper.readValue(res, FlashcardGenerateListResponse.class);

            if (generatedFlashcards.flashcards() == null)
                throw new LLMResponseParsingException("Failed to parse LLM response");

            flashcards.addAll(generatedFlashcards.flashcards());

            persistenceService.saveFlashcardFromNote(flashcards, userId, note);

            return new FlashcardGenerateListResponse(flashcards);
        } catch (JacksonException e) {
            throw new LLMResponseParsingException("Failed to parse LLM response");
        }
    }

}

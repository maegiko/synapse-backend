package com.synapse.backend.flashcards;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.ai.exceptions.LLMResponseParsingException;
import com.synapse.backend.ai.prompts.FlashcardGeneratePromptFactory;
import com.synapse.backend.flashcards.dto.AddFlashcardRequest;
import com.synapse.backend.flashcards.dto.AddFlashcardResponse;
import com.synapse.backend.flashcards.dto.FlashcardResponse;
import com.synapse.backend.flashcards.dto.generate.FlashcardGenerateListResponse;
import com.synapse.backend.flashcards.dto.generate.FlashcardGenerateResponse;
import com.synapse.backend.flashcards.dto.generate.FlashcardSourceNote;
import com.synapse.backend.notes.NotesService;
import com.synapse.backend.notes.dto.NoteForGeneration;
import com.synapse.backend.notes.dto.NoteSummaryResponse;
import com.synapse.backend.shared.exceptions.concrete.UserUnauthorised;

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

    /**
     * Creates and generates a list of flashcards from a note and saves each flashcard to the DB.
     *
     * @param noteId the id of the note to generate flashcards from.
     * @param userId the id of the currently authenticated user.
     * @return the newly created list of flashcards.
     */
    public FlashcardGenerateResponse generateFlashCards(UUID noteId, Long userId) {
        NoteForGeneration note = notesService.getNoteForGeneration(noteId, userId);

        List<FlashcardResponse> flashcards = getBasicFlashcardsFromNote(note.summary());

        LLMRequest req = new LLMRequest(
            "openai/gpt-oss-120b",
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

            UUID deckId = persistenceService
                .saveFlashcardFromNote(flashcards, userId, new FlashcardSourceNote(note.id(), note.summary().title()));

            return new FlashcardGenerateResponse(deckId, flashcards);
        } catch (JacksonException e) {
            throw new LLMResponseParsingException("Failed to parse LLM response");
        }
    }

    private List<FlashcardResponse> getBasicFlashcardsFromNote(NoteSummaryResponse note) {
        return new ArrayList<>(
            note.concepts()
                .stream()
                .map(c -> new FlashcardResponse(c.name(), c.explanation()))
                .toList()
        );
    }

    /**
     * Adds a new flashcard to a deck owned by the currently authenticated user.
     *
     * @param deckId the public id of the deck to add the flashcard to.
     * @param userId the id of the currently authenticated user.
     * @param req the flashcard question and answer to save.
     * @return the newly created flashcard.
     */
    public AddFlashcardResponse addFlashcard(UUID deckId, Long userId, AddFlashcardRequest req) {
        if (userId == null)
            throw new UserUnauthorised("User not authorised.");

        return persistenceService.addFlashcard(deckId, userId, req);
    }

    /**
     * Deletes a flashcard from a deck owned by the currently authenticated user.
     *
     * @param userId the id of the currently authenticated user.
     * @param deckId the public id of the flashcard deck.
     * @param flashcardId the public id of the flashcard to delete.
     */
    public void deleteFlashcard(Long userId, UUID deckId, UUID flashcardId) {
        persistenceService.deleteFlashcard(userId, deckId, flashcardId);
    }

}

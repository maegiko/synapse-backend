package com.synapse.backend.flashcards;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.ai.exceptions.LLMResponseParsingException;
import com.synapse.backend.ai.prompts.FlashcardGeneratePromptFactory;
import com.synapse.backend.flashcards.dto.AddFlashcardRequest;
import com.synapse.backend.flashcards.dto.AddFlashcardResponse;
import com.synapse.backend.flashcards.dto.FlashcardResponse;
import com.synapse.backend.flashcards.dto.UpdateFlashcardRequest;
import com.synapse.backend.flashcards.exceptions.InvalidFlashcardException;
import com.synapse.backend.flashcards.dto.generate.FlashcardGenerateListResponse;
import com.synapse.backend.flashcards.dto.generate.FlashcardGenerateResponse;
import com.synapse.backend.flashcards.dto.generate.FlashcardSourceNote;
import com.synapse.backend.flashcards.dto.review.ReviewDeckResponse;
import com.synapse.backend.flashcards.enums.ReviewRating;
import com.synapse.backend.notes.NotesService;
import com.synapse.backend.notes.dto.NoteForGeneration;
import com.synapse.backend.notes.dto.NoteSummaryResponse;
import com.synapse.backend.shared.exceptions.concrete.UserUnauthorised;
import com.synapse.backend.streak.StreakService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class FlashcardService {
    private final NotesService notesService;
    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;
    private final FlashcardGeneratePromptFactory promptFactory;
    private final FlashcardPersistenceService persistenceService;
    private final StreakService streakService;

    public FlashcardService(
        NotesService notesService,
        LLMClient llmClient,
        ObjectMapper objectMapper,
        FlashcardGeneratePromptFactory promptFactory,
        FlashcardPersistenceService persistenceService,
        StreakService streakService
    ) {
        this.notesService = notesService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.promptFactory = promptFactory;
        this.persistenceService = persistenceService;
        this.streakService = streakService;
    }

    /**
     * Creates and generates a list of flashcards from a note and saves each flashcard to the DB.
     *
     * <p>Study activity is recorded once the deck has been saved.</p>
     *
     * @param noteId the id of the note to generate flashcards from.
     * @param userId the id of the currently authenticated user.
     * @return the newly created list of flashcards.
     */
    public FlashcardGenerateResponse generateFlashCards(String noteId, Long userId) {
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

            String deckId = persistenceService
                .saveFlashcardFromNote(flashcards, userId, new FlashcardSourceNote(note.id(), note.summary().title()));

            streakService.recordActivity(userId);

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
    public AddFlashcardResponse addFlashcard(String deckId, Long userId, AddFlashcardRequest req) {
        if (userId == null)
            throw new UserUnauthorised("User not authorised.");

        return persistenceService.addFlashcard(deckId, userId, req);
    }

    /**
     * Updates the question and/or answer of a flashcard in a deck owned by the currently
     * authenticated user.
     *
     * <p>Only the supplied fields are changed. The request arrives with its question and answer
     * trimmed.</p>
     *
     * @param deckId the public id of the flashcard deck.
     * @param userId the id of the currently authenticated user.
     * @param cardId the public id of the flashcard to update.
     * @param req the validated fields to update, with at least one field supplied.
     * @return the updated flashcard.
     * @throws InvalidFlashcardException if no field is supplied or a supplied field is blank.
     */
    public AddFlashcardResponse updateFlashcard(String deckId, Long userId, String cardId, UpdateFlashcardRequest req) {
        String question = req.question();
        String answer = req.answer();

        if (question == null && answer == null)
            throw new InvalidFlashcardException("At least one of question or answer must be supplied.");

        if (question != null && question.isBlank())
            throw new InvalidFlashcardException("question: must not be blank");

        if (answer != null && answer.isBlank())
            throw new InvalidFlashcardException("answer: must not be blank");

        return persistenceService.updateFlashcard(deckId, userId, cardId, question, answer);
    }

    /**
     * Deletes a flashcard from a deck owned by the currently authenticated user.
     *
     * @param userId the id of the currently authenticated user.
     * @param deckId the public id of the flashcard deck.
     * @param flashcardId the public id of the flashcard to delete.
     */
    public void deleteFlashcard(Long userId, String deckId, String flashcardId) {
        persistenceService.deleteFlashcard(userId, deckId, flashcardId);
    }

    /**
     * Records a review of a flashcard deck owned by the currently authenticated user.
     *
     * <p>The deck is rescheduled, the review is saved to the deck's history, and the user's
     * lifetime cards-reviewed count is increased by the deck's card count. Study activity is
     * recorded once the review has been saved, so a failed ownership check or an empty deck
     * awards nothing.</p>
     *
     * @param deckId the public id of the reviewed deck.
     * @param userId the id of the currently authenticated user.
     * @param rating how well the user recalled the deck.
     * @return the applied rating, new schedule, cards reviewed, and the user's lifetime count.
     */
    public ReviewDeckResponse reviewDeck(String deckId, Long userId, ReviewRating rating) {
        ReviewDeckResponse res = persistenceService.reviewDeck(deckId, userId, rating);

        streakService.recordActivity(userId);

        return res;
    }

}

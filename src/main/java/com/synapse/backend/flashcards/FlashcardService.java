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
import com.synapse.backend.flashcards.dto.UpdateDeckRequest;
import com.synapse.backend.flashcards.dto.UpdateFlashcardRequest;
import com.synapse.backend.flashcards.exceptions.InvalidDeckException;
import com.synapse.backend.flashcards.exceptions.InvalidFlashcardException;
import com.synapse.backend.flashcards.dto.generate.FlashcardGenerateListResponse;
import com.synapse.backend.flashcards.dto.generate.FlashcardGenerateResponse;
import com.synapse.backend.flashcards.dto.generate.FlashcardSourceNote;
import com.synapse.backend.flashcards.dto.list.SingleDeckResponse;
import com.synapse.backend.flashcards.dto.review.ReviewDeckResponse;
import com.synapse.backend.flashcards.enums.ReviewRating;
import com.synapse.backend.notes.NotesService;
import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;
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

            generatedFlashcards.flashcards().stream().map(this::withinLimits).forEach(flashcards::add);

            String deckId = persistenceService
                .saveFlashcardFromNote(flashcards, userId, new FlashcardSourceNote(note.id(), note.summary().title()));

            streakService.recordActivity(userId);

            // A newly generated deck is always unpinned; the user pins it later through PATCH.
            return new FlashcardGenerateResponse(deckId, false, flashcards);
        } catch (JacksonException e) {
            throw new LLMResponseParsingException("Failed to parse LLM response");
        }
    }

    private List<FlashcardResponse> getBasicFlashcardsFromNote(NoteSummaryResponse note) {
        return new ArrayList<>(
            note.concepts()
                .stream()
                .map(c -> withinLimits(new FlashcardResponse(c.name(), c.explanation())))
                .toList()
        );
    }

    /**
     * Clamps a card to the bounds the add and edit endpoints enforce.
     *
     * <p>A card's two sides come either from a note concept or straight from the model, and
     * neither is bounded at the source. Storing one longer than PATCH
     * /api/flashcards/{deckId}/cards/{cardId} accepts would leave the user unable to save
     * that card again.</p>
     *
     * @param card the generated card.
     * @return the same card with both sides bounded.
     */
    private FlashcardResponse withinLimits(FlashcardResponse card) {
        return new FlashcardResponse(
            RequestText.clamped(card.title(), ValidationLimits.FLASHCARD_TEXT_MAX),
            RequestText.clamped(card.answer(), ValidationLimits.FLASHCARD_TEXT_MAX)
        );
    }

    /**
     * Updates the title and/or pin state of a deck owned by the currently authenticated user.
     *
     * <p>Only the supplied fields are changed. The request arrives with its title trimmed. The
     * pin state is null when it was not supplied, true to pin the deck, and false to unpin it.</p>
     *
     * @param deckId the public id of the deck.
     * @param userId the id of the currently authenticated user.
     * @param req the validated fields to update, with at least one field supplied.
     * @return the updated deck with its cards in position order.
     * @throws InvalidDeckException if no field is supplied.
     */
    public SingleDeckResponse updateDeck(String deckId, Long userId, UpdateDeckRequest req) {
        String title = req.title();
        Boolean pinned = req.pinned();

        if (title == null && pinned == null)
            throw new InvalidDeckException("At least one of title or pinned must be supplied.");

        return persistenceService.updateDeck(deckId, userId, title, pinned);
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
     * @throws InvalidFlashcardException if no field is supplied.
     */
    public AddFlashcardResponse updateFlashcard(String deckId, Long userId, String cardId, UpdateFlashcardRequest req) {
        String question = req.question();
        String answer = req.answer();

        if (question == null && answer == null)
            throw new InvalidFlashcardException("At least one of question or answer must be supplied.");

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
     * @param durationSeconds how long the session took, or null when the client did not report it.
     * @return the applied rating, new schedule, cards reviewed, and the user's lifetime count.
     */
    public ReviewDeckResponse reviewDeck(
        String deckId,
        Long userId,
        ReviewRating rating,
        Integer durationSeconds
    ) {
        ReviewDeckResponse res = persistenceService.reviewDeck(deckId, userId, rating, durationSeconds);

        streakService.recordActivity(userId);

        return res;
    }

}

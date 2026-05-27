package com.synapse.backend.flashcards;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.synapse.backend.flashcards.dto.FlashcardResponse;
import com.synapse.backend.flashcards.dto.generate.FlashcardSourceNote;
import com.synapse.backend.flashcards.dto.list.FlashcardListResponse;
import com.synapse.backend.flashcards.dto.list.FlashcardWithIdResponse;
import com.synapse.backend.flashcards.dto.list.SingleDeckResponse;
import com.synapse.backend.flashcards.entities.Flashcard;
import com.synapse.backend.flashcards.entities.FlashcardDeck;
import com.synapse.backend.flashcards.exceptions.DeckNotFound;
import com.synapse.backend.flashcards.repositories.FlashcardDeckRepository;
import com.synapse.backend.flashcards.repositories.FlashcardRepository;

import jakarta.transaction.Transactional;

@Service
public class FlashcardPersistenceService {
    private final FlashcardDeckRepository flashcardDeckRepository;
    private final FlashcardRepository flashcardRepository;

    public FlashcardPersistenceService(
        FlashcardDeckRepository flashcardDeckRepository,
        FlashcardRepository flashcardRepository
    ) {
        this.flashcardDeckRepository = flashcardDeckRepository;
        this.flashcardRepository = flashcardRepository;
    }

    /**
     * Saves a deck and flashcards generated from a note summary to the DB.
     *
     * @param flashcards list of flashcards to save.
     * @param userId the id of the currently authenticated user.
     * @param note the note that the flashcards were generated from.
     * @return the deck id if available, else null.
     */
    @Transactional
    public UUID saveFlashcardFromNote(List<FlashcardResponse> flashcards, Long userId, FlashcardSourceNote note) {
        if (flashcards == null)
            return null;

        UUID deckId = UUID.randomUUID();

        FlashcardDeck flashcardDeck = new FlashcardDeck(userId, note.id(), note.title(), "NOTE", deckId);
        FlashcardDeck newFlashcardDeck = flashcardDeckRepository.save(flashcardDeck);

        List<Flashcard> newFlashcards = new ArrayList<>();

        for (int i = 0; i < flashcards.size(); i++) {
            FlashcardResponse flashcard = flashcards.get(i);
            newFlashcards.add(
                new Flashcard(newFlashcardDeck.getId(), flashcard.title(), flashcard.answer(), i, UUID.randomUUID())
            );
        }

        flashcardRepository.saveAll(newFlashcards);

        return deckId;
    }

    /**
     * Returns all flashcards owned by user, sorted by deck.
     *
     * @param userId the id of the currently authenticated user.
     * @return a list of all flashcard decks and cards owned by the user.
     */
    public FlashcardListResponse getAllFlashcards(Long userId) {
        List<FlashcardDeck> decks = flashcardDeckRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Long> deckIds = decks.stream().map(FlashcardDeck::getId).toList();

        Map<Long, List<Flashcard>> flashcards = flashcardRepository.findByDeckIdInOrderByDeckIdAscPositionAsc(deckIds)
            .stream()
            .collect(Collectors.groupingBy(Flashcard::getDeckId));

        List<SingleDeckResponse> flashcardList = new ArrayList<>();

        for (FlashcardDeck deck : decks) {
            List<FlashcardWithIdResponse> cards = flashcards
                .getOrDefault(deck.getId(), List.of())
                .stream()
                .map(c -> new FlashcardWithIdResponse(c.getPublicId(), c.getQuestion(), c.getAnswer()))
                .toList();

            flashcardList.add(
                new SingleDeckResponse(deck.getPublicId(), cards)
            );
        }

        return new FlashcardListResponse(flashcardList);
    }

    /**
     * Returns a single deck of flashcards.
     * @param publicId the public id of the flashcard deck.
     * @param userId the id of the currently authenticated user.
     * @return a deck id and a list of flashcards in the deck.
     * @throws DeckNotFound if a deck with a given public deck id doesn't exist for this user.
     */
    public SingleDeckResponse getSingleFlashcardDeck(UUID publicId, Long userId) {
        FlashcardDeck deck = flashcardDeckRepository
            .findByPublicIdAndUserId(publicId, userId)
            .orElseThrow(() -> new DeckNotFound("Flashcard deck not found: " + publicId));

        List<Flashcard> flashcards = flashcardRepository.findByDeckIdOrderByPositionAsc(deck.getId());

        List<FlashcardWithIdResponse> flashcardList = flashcards
                .stream()
                .map(f -> new FlashcardWithIdResponse(f.getPublicId(), f.getQuestion(), f.getAnswer()))
                .toList();

        return new SingleDeckResponse(publicId, flashcardList);
    }

    /**
     * Deletes a deck and all its flashcards from the DB.
     * @param publicId the public id of the deck.
     * @param userId the id of the currently authenticated user.
     * @throws DeckNotFound if the deck doesn't exist.
     */
    @Transactional
    public void deleteDeck(UUID publicId, Long userId) {
        long isDeleted = flashcardDeckRepository.deleteByPublicIdAndUserId(publicId, userId);

        if (isDeleted == 0)
            throw new DeckNotFound("Flashcard deck not found: " + publicId);
    }

}

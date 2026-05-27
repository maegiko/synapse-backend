package com.synapse.backend.flashcards;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.synapse.backend.flashcards.dto.FlashcardResponse;
import com.synapse.backend.flashcards.dto.list.DeckListResponse;
import com.synapse.backend.flashcards.dto.list.FlashcardListResponse;
import com.synapse.backend.flashcards.dto.list.FlashcardWithIdResponse;
import com.synapse.backend.flashcards.entities.Flashcard;
import com.synapse.backend.flashcards.entities.FlashcardDeck;
import com.synapse.backend.flashcards.repositories.FlashcardDeckRepository;
import com.synapse.backend.flashcards.repositories.FlashcardRepository;
import com.synapse.backend.notes.dto.NoteSummaryResponse;

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
     * Saves a flashcard generated from a note summary to the DB.
     * @param flashcards list of flashcards to save.
     * @param userId the id of the currently authenticated user.
     * @param note the note that the flashcards were generated from.
     * @return the deck id if available, else null.
     */
    @Transactional
    public UUID saveFlashcardFromNote(List<FlashcardResponse> flashcards, Long userId, NoteSummaryResponse note) {
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
     * @param userId the id of the currently authenticated user.
     * @return a list of all flashcard decks and cards owned by the user.
     */
    public FlashcardListResponse getAllFlashcards(Long userId) {
        List<FlashcardDeck> decks = flashcardDeckRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Long> deckIds = decks.stream().map(FlashcardDeck::getId).toList();

        Map<Long, List<Flashcard>> flashcards = flashcardRepository.findByDeckIdInOrderByDeckIdAscPositionAsc(deckIds)
            .stream()
            .collect(Collectors.groupingBy(Flashcard::getDeckId));

        List<DeckListResponse> flashcardList = new ArrayList<>();

        for (FlashcardDeck deck : decks) {
            List<FlashcardWithIdResponse> cards = flashcards
                .getOrDefault(deck.getId(), List.of())
                .stream()
                .map(c -> new FlashcardWithIdResponse(c.getPublicId(), c.getQuestion(), c.getAnswer()))
                .toList();

            flashcardList.add(
                new DeckListResponse(deck.getPublicId(), cards)
            );
        }

        return new FlashcardListResponse(flashcardList);
    }

}

package com.synapse.backend.flashcards;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.synapse.backend.flashcards.dto.FlashcardGenerateResponse;
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
     */
    @Transactional
    public void saveFlashcardFromNote(List<FlashcardGenerateResponse> flashcards, Long userId, NoteSummaryResponse note) {
        if (flashcards == null) return;

        FlashcardDeck flashcardDeck = new FlashcardDeck(userId, note.id(), note.title(), "NOTE");
        FlashcardDeck newFlashcardDeck = flashcardDeckRepository.save(flashcardDeck);

        List<Flashcard> newFlashcards = new ArrayList<>();

        for (int i = 0; i < flashcards.size(); i++) {
            FlashcardGenerateResponse flashcard = flashcards.get(i);
            newFlashcards.add(new Flashcard(newFlashcardDeck.getId(), flashcard.title(), flashcard.answer(), i));
        }

        flashcardRepository.saveAll(newFlashcards);
    }

}

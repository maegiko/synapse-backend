package com.synapse.backend.flashcards.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.synapse.backend.flashcards.entities.Flashcard;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    List<Flashcard> findByDeckIdOrderByPositionAsc(Long deckId);

}

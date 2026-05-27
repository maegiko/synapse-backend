package com.synapse.backend.flashcards.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.synapse.backend.flashcards.entities.FlashcardDeck;

public interface FlashcardDeckRepository extends JpaRepository<FlashcardDeck, Long> {

    List<FlashcardDeck> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<FlashcardDeck> findByIdAndUserId(Long deckId, Long userId);

    Optional<FlashcardDeck> findByPublicIdAndUserId(UUID publicId, Long userId);

}

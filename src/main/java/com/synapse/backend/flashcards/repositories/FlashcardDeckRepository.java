package com.synapse.backend.flashcards.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.flashcards.entities.FlashcardDeck;

public interface FlashcardDeckRepository extends JpaRepository<FlashcardDeck, Long> {

    List<FlashcardDeck> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<FlashcardDeck> findByIdAndUserId(Long deckId, Long userId);

    Optional<FlashcardDeck> findByPublicIdAndUserId(UUID publicId, Long userId);

    long deleteByPublicIdAndUserId(UUID publicId, Long userId);

    @Modifying
    @Query("""
        UPDATE FlashcardDeck d
        SET d.updatedAt = CURRENT_TIMESTAMP
        WHERE d.id = :deckId
    """)
    long updateUpdatedAtById(@Param("deckId") Long deckId);

}

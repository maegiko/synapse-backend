package com.synapse.backend.flashcards.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.flashcards.entities.Flashcard;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    List<Flashcard> findByDeckIdOrderByPositionAsc(Long deckId);

    List<Flashcard> findByDeckIdInOrderByDeckIdAscPositionAsc(List<Long> deckIds);

    @Query("SELECT MAX(f.position) FROM Flashcard f WHERE f.deckId = :deckId")
    Optional<Integer> findMaxPositionByDeckId(@Param("deckId") Long deckId);

    long deleteByPublicIdAndDeckId(UUID publicId, Long deckId);

}

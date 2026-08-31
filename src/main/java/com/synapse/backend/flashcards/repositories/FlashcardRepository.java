package com.synapse.backend.flashcards.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.flashcards.entities.Flashcard;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    List<Flashcard> findByDeckIdOrderByPositionAsc(Long deckId);

    List<Flashcard> findByDeckIdInOrderByDeckIdAscPositionAsc(List<Long> deckIds);

    int countByDeckId(Long deckId);

    @Query("SELECT MAX(f.position) FROM Flashcard f WHERE f.deckId = :deckId")
    Optional<Integer> findMaxPositionByDeckId(@Param("deckId") Long deckId);

    Optional<Flashcard> findByPublicIdAndDeckId(String publicId, Long deckId);

    long deleteByPublicIdAndDeckId(String publicId, Long deckId);

}

package com.synapse.backend.flashcards.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.flashcards.entities.FlashcardDeck;

import jakarta.persistence.LockModeType;

public interface FlashcardDeckRepository extends JpaRepository<FlashcardDeck, Long> {

    List<FlashcardDeck> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<FlashcardDeck> findByUserIdAndNextReviewDateLessThanEqualOrderByNextReviewDateAscIdAsc(
        Long userId,
        LocalDate nextReviewDate
    );

    Optional<FlashcardDeck> findByIdAndUserId(Long deckId, Long userId);

    Optional<FlashcardDeck> findByPublicIdAndUserId(String publicId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT d FROM FlashcardDeck d
        WHERE d.publicId = :publicId AND d.userId = :userId
    """)
    Optional<FlashcardDeck> findByPublicIdAndUserIdForReview(
        @Param("publicId") String publicId,
        @Param("userId") Long userId
    );

    long deleteByPublicIdAndUserId(String publicId, Long userId);

    @Modifying
    @Query("""
        UPDATE FlashcardDeck d
        SET d.updatedAt = CURRENT_TIMESTAMP
        WHERE d.id = :deckId
    """)
    long updateUpdatedAtById(@Param("deckId") Long deckId);

}

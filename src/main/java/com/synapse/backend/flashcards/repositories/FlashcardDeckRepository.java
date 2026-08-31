package com.synapse.backend.flashcards.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.flashcards.entities.FlashcardDeck;

import jakarta.persistence.LockModeType;

public interface FlashcardDeckRepository extends JpaRepository<FlashcardDeck, Long> {

    List<FlashcardDeck> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<FlashcardDeck> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<FlashcardDeck> findByUserIdAndTitleContainingIgnoreCaseOrderByCreatedAtDescIdDesc(
        Long userId,
        String title,
        Pageable pageable
    );

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

    List<FlashcardDeck> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    int countByGroupId(Long groupId);

    @Modifying
    @Query("""
        UPDATE FlashcardDeck d
        SET d.groupId = :groupId
        WHERE d.publicId = :publicId AND d.userId = :userId
    """)
    long updateGroupIdByPublicIdAndUserId(
        @Param("publicId") String publicId,
        @Param("userId") Long userId,
        @Param("groupId") Long groupId
    );

    @Modifying
    @Query("""
        UPDATE FlashcardDeck d
        SET d.groupId = NULL
        WHERE d.publicId = :publicId AND d.userId = :userId AND d.groupId = :groupId
    """)
    long clearGroupIdByPublicIdAndUserId(
        @Param("publicId") String publicId,
        @Param("userId") Long userId,
        @Param("groupId") Long groupId
    );

    @Modifying
    @Query("""
        UPDATE FlashcardDeck d
        SET d.updatedAt = CURRENT_TIMESTAMP
        WHERE d.id = :deckId
    """)
    long updateUpdatedAtById(@Param("deckId") Long deckId);

}

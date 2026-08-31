package com.synapse.backend.flashcards.repositories;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.analytics.dto.RatingCount;
import com.synapse.backend.flashcards.entities.FlashcardDeckReview;

public interface FlashcardDeckReviewRepository extends JpaRepository<FlashcardDeckReview, Long> {

    /**
     * One row per local day the user reviewed on, summed in the database rather than by
     * loading the history. Review instants are stored as UTC, so they are converted to the
     * user's zone before the calendar date is taken; a session at 23:00 UTC belongs to the
     * next day for a user in Sydney.
     *
     * @param userId the id of the authenticated user.
     * @param zone the user's IANA time zone, which decides where each day starts.
     * @param fromUtc the inclusive UTC start of the window.
     * @param toUtc the exclusive UTC end of the window.
     * @return the user's daily totals, oldest day first.
     */
    @Query(value = """
        SELECT (r.reviewed_at AT TIME ZONE 'UTC' AT TIME ZONE CAST(:zone AS text))::date AS "day",
               SUM(r.cards_reviewed) AS "cardsReviewed",
               COUNT(*) AS "reviewSessions",
               COALESCE(SUM(r.duration_seconds), 0) AS "studySeconds"
        FROM flashcard_deck_review r
        JOIN flashcard_deck d ON d.id = r.deck_id
        WHERE d.user_id = :userId
          AND r.reviewed_at >= :fromUtc
          AND r.reviewed_at < :toUtc
        GROUP BY "day"
        ORDER BY "day"
    """, nativeQuery = true)
    List<DailyDeckReviewProjection> findDailyTotals(
        @Param("userId") Long userId,
        @Param("zone") String zone,
        @Param("fromUtc") LocalDateTime fromUtc,
        @Param("toUtc") LocalDateTime toUtc
    );

    /**
     * Counts the window's reviews by rating. Ownership is applied through the deck, because a
     * review row belongs to a deck rather than directly to a user.
     *
     * @param userId the id of the authenticated user.
     * @param fromUtc the inclusive UTC start of the window.
     * @param toUtc the exclusive UTC end of the window.
     * @return one row per rating that occurs in the window.
     */
    @Query("""
        SELECT new com.synapse.backend.analytics.dto.RatingCount(r.rating, COUNT(r))
        FROM FlashcardDeckReview r
        WHERE r.deckId IN (SELECT d.id FROM FlashcardDeck d WHERE d.userId = :userId)
          AND r.reviewedAt >= :fromUtc
          AND r.reviewedAt < :toUtc
        GROUP BY r.rating
    """)
    List<RatingCount> countRatings(
        @Param("userId") Long userId,
        @Param("fromUtc") LocalDateTime fromUtc,
        @Param("toUtc") LocalDateTime toUtc
    );

}

package com.synapse.backend.flashcards.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.synapse.backend.flashcards.enums.ReviewRating;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "flashcard_deck_review")
public class FlashcardDeckReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deck_id", nullable = false)
    private Long deckId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReviewRating rating;

    @Column(name = "cards_reviewed", nullable = false)
    private int cardsReviewed;

    @Column(name = "total_cards", nullable = false)
    private int totalCards;

    @Column(name = "previous_interval_days", nullable = false)
    private int previousIntervalDays;

    @Column(name = "new_interval_days", nullable = false)
    private int newIntervalDays;

    @Column(name = "previous_ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal previousEaseFactor;

    @Column(name = "new_ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal newEaseFactor;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    protected FlashcardDeckReview() {}

    public FlashcardDeckReview(
        Long deckId,
        ReviewRating rating,
        int cardsReviewed,
        int totalCards,
        int previousIntervalDays,
        int newIntervalDays,
        BigDecimal previousEaseFactor,
        BigDecimal newEaseFactor,
        LocalDateTime reviewedAt
    ) {
        this.deckId = deckId;
        this.rating = rating;
        this.cardsReviewed = cardsReviewed;
        this.totalCards = totalCards;
        this.previousIntervalDays = previousIntervalDays;
        this.newIntervalDays = newIntervalDays;
        this.previousEaseFactor = previousEaseFactor;
        this.newEaseFactor = newEaseFactor;
        this.reviewedAt = reviewedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getDeckId() {
        return deckId;
    }

    public ReviewRating getRating() {
        return rating;
    }

    public int getCardsReviewed() {
        return cardsReviewed;
    }

    public int getTotalCards() {
        return totalCards;
    }

    public int getPreviousIntervalDays() {
        return previousIntervalDays;
    }

    public int getNewIntervalDays() {
        return newIntervalDays;
    }

    public BigDecimal getPreviousEaseFactor() {
        return previousEaseFactor;
    }

    public BigDecimal getNewEaseFactor() {
        return newEaseFactor;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

}

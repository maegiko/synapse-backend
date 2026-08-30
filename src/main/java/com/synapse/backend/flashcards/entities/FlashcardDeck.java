package com.synapse.backend.flashcards.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "flashcard_deck")
public class FlashcardDeck {

    private static final BigDecimal DEFAULT_EASE_FACTOR = new BigDecimal("2.50");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "note_id", nullable = true)
    private Long noteId;

    @Column(nullable = false)
    private String title;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "public_id", nullable = false, unique = true, length = 10)
    private String publicId;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(name = "ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal easeFactor;

    @Column(name = "next_review_date", nullable = false)
    private LocalDate nextReviewDate;

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected FlashcardDeck() {}

    public FlashcardDeck(Long userId, Long noteId, String title, String sourceType, LocalDate today) {
        this.userId = userId;
        this.noteId = noteId;
        this.title = title;
        this.sourceType = sourceType;
        this.publicId = NanoIdUtils.randomNanoId(
            NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
            NanoIdUtils.DEFAULT_ALPHABET,
            10
        );
        this.reviewCount = 0;
        this.intervalDays = 0;
        this.easeFactor = DEFAULT_EASE_FACTOR;
        this.nextReviewDate = today;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getNoteId() {
        return noteId;
    }

    public String getTitle() {
        return title;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getPublicId() {
        return publicId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public BigDecimal getEaseFactor() {
        return easeFactor;
    }

    public LocalDate getNextReviewDate() {
        return nextReviewDate;
    }

    public LocalDateTime getLastReviewedAt() {
        return lastReviewedAt;
    }

    /**
     * Applies a completed review to the deck's spaced repetition schedule.
     *
     * @param intervalDays the new interval in whole days.
     * @param easeFactor the new ease factor.
     * @param nextReviewDate the UTC date the deck is next due on.
     * @param reviewedAt the time the review was completed.
     */
    public void applyReview(
        int intervalDays,
        BigDecimal easeFactor,
        LocalDate nextReviewDate,
        LocalDateTime reviewedAt
    ) {
        this.reviewCount += 1;
        this.intervalDays = intervalDays;
        this.easeFactor = easeFactor;
        this.nextReviewDate = nextReviewDate;
        this.lastReviewedAt = reviewedAt;
    }

}

package com.synapse.backend.flashcards.dto.review;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.synapse.backend.flashcards.enums.ReviewRating;

public record ReviewQueueDeckResponse(
    String deckId,
    String title,
    int cardCount,
    LocalDate nextReviewDate,
    int intervalDays,
    int reviewCount,
    LocalDateTime lastReviewedAt,
    ReviewRating lastRating
) {}

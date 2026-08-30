package com.synapse.backend.flashcards.dto.review;

import java.time.LocalDate;

import com.synapse.backend.flashcards.enums.ReviewRating;

public record ReviewDeckResponse(
    String deckId,
    ReviewRating rating,
    int intervalDays,
    LocalDate nextReviewDate,
    int cardsReviewed,
    long totalFlashcardsReviewed
) {}

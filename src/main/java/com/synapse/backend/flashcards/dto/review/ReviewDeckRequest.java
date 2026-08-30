package com.synapse.backend.flashcards.dto.review;

import com.synapse.backend.flashcards.enums.ReviewRating;

import jakarta.validation.constraints.NotNull;

public record ReviewDeckRequest(
    @NotNull
    ReviewRating rating
) {}

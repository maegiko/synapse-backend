package com.synapse.backend.analytics.dto;

import com.synapse.backend.flashcards.enums.ReviewRating;

/** One row of the grouped rating count query, before it is folded into {@link RatingCountsResponse}. */
public record RatingCount(
    ReviewRating rating,
    long total
) {}

package com.synapse.backend.flashcards.dto.review;

import com.synapse.backend.flashcards.enums.ReviewRating;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A finished study session. {@code durationSeconds} is how long the session took and is
 * optional: a client that does not time its sessions omits it, and the review is saved
 * with no duration rather than a guessed one.
 */
public record ReviewDeckRequest(
    @NotNull
    ReviewRating rating,

    @Min(0)
    @Max(21600)
    Integer durationSeconds
) {

    /** A review from a client that does not report how long the session took. */
    public ReviewDeckRequest(ReviewRating rating) {
        this(rating, null);
    }

}

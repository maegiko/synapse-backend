package com.synapse.backend.quiz.dto.score;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A finished quiz attempt. {@code durationSeconds} is how long the attempt took and is
 * optional: a client that does not time its attempts omits it, and the score is saved
 * with no duration rather than a guessed one.
 */
public record SaveScoreRequest(
    @NotNull
    @Min(0)
    Integer score,

    @Min(0)
    @Max(21600)
    Integer durationSeconds
) {

    /** An attempt from a client that does not report how long it took. */
    public SaveScoreRequest(Integer score) {
        this(score, null);
    }

}

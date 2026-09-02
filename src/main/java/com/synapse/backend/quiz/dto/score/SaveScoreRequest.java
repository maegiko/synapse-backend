package com.synapse.backend.quiz.dto.score;

import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A finished quiz attempt. {@code durationSeconds} is how long the attempt took and is
 * optional: a client that does not time its attempts omits it, and the score is saved with
 * no duration rather than a guessed one.
 *
 * <p>The score's upper bound is the quiz's own question count, which is checked when the
 * attempt is saved because only then is the quiz known.</p>
 */
public record SaveScoreRequest(
    @NotNull
    @Min(0)
    Integer score,

    @Min(0)
    @Max(ValidationLimits.DURATION_SECONDS_MAX)
    Integer durationSeconds
) {

    /** An attempt from a client that does not report how long it took. */
    public SaveScoreRequest(Integer score) {
        this(score, null);
    }

}

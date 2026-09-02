package com.synapse.backend.quiz.dto;

import com.synapse.backend.shared.validation.NullOrNotBlank;
import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.Size;

/**
 * Optional quiz fields to update, normalised on construction so the constraints below are
 * checked against the values that are persisted.
 *
 * <p>The bounds match the ones the generated quiz is clamped to, so a quiz can always be
 * saved again with the title and description it was created with. A blank description clears
 * it, which is why the description is not {@code @NullOrNotBlank}.</p>
 *
 * <p>{@code pinned} is null when the pin state was not supplied and must be left unchanged,
 * {@code true} to pin the quiz, and {@code false} to unpin it.</p>
 */
public record UpdateQuizRequest(
    @NullOrNotBlank
    @Size(max = ValidationLimits.TITLE_MAX)
    String title,

    @Size(max = ValidationLimits.DESCRIPTION_MAX)
    String description,

    Boolean pinned
) {

    public UpdateQuizRequest {
        title = RequestText.trimmed(title);
        description = RequestText.trimmed(description);
    }

}

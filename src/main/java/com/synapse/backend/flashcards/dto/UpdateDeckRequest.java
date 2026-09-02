package com.synapse.backend.flashcards.dto;

import com.synapse.backend.shared.validation.NullOrNotBlank;
import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.Size;

/**
 * Optional flashcard deck fields to update, normalised on construction so the constraints
 * below are checked against the value that is actually persisted.
 *
 * <p>The title bound matches the one the generated deck title is clamped to, so a deck can
 * always be saved again with the title it was created with.</p>
 *
 * <p>{@code pinned} is null when the pin state was not supplied and must be left unchanged,
 * {@code true} to pin the deck, and {@code false} to unpin it.</p>
 */
public record UpdateDeckRequest(
    @NullOrNotBlank
    @Size(max = ValidationLimits.TITLE_MAX)
    String title,

    Boolean pinned
) {

    public UpdateDeckRequest {
        title = RequestText.trimmed(title);
    }

}

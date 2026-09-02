package com.synapse.backend.notes.dto;

import com.synapse.backend.shared.validation.NullOrNotBlank;
import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.Size;

/**
 * Optional note fields to update, normalised on construction so the constraints below are
 * checked against the values that are persisted.
 *
 * <p>The bounds match the ones the generated summary is clamped to, so a note can always be
 * saved again with the title and overview it was created with.</p>
 *
 * <p>{@code pinned} is null when the pin state was not supplied and must be left unchanged,
 * {@code true} to pin the note, and {@code false} to unpin it.</p>
 */
public record UpdateNoteRequest(
    @NullOrNotBlank
    @Size(max = ValidationLimits.TITLE_MAX)
    String title,

    @NullOrNotBlank
    @Size(max = ValidationLimits.OVERVIEW_MAX)
    String overview,

    Boolean pinned
) {

    public UpdateNoteRequest {
        title = RequestText.trimmed(title);
        overview = RequestText.trimmed(overview);
    }

}

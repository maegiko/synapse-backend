package com.synapse.backend.flashcards.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The deck title to update, normalised on construction so the
 * constraint below is checked against the value that is persisted.
 */
public record UpdateDeckRequest(
    @NotBlank
    String title
) {

    public UpdateDeckRequest {
        title = title == null ? null : title.trim();
    }

}

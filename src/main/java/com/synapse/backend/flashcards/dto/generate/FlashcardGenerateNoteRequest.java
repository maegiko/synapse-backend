package com.synapse.backend.flashcards.dto.generate;

import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The note to generate a deck from, addressed by its public id.
 */
public record FlashcardGenerateNoteRequest(
    @NotBlank
    @Size(max = ValidationLimits.PUBLIC_ID_MAX)
    String noteId
) {

    public FlashcardGenerateNoteRequest {
        noteId = RequestText.trimmed(noteId);
    }

}

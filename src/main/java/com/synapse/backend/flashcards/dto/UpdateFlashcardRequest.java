package com.synapse.backend.flashcards.dto;

import com.synapse.backend.shared.validation.NullOrNotBlank;
import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.Size;

/**
 * Optional flashcard fields to update, normalised on construction so the constraints below
 * are checked against the values that are persisted. Both sides carry the same bounds the
 * add endpoint applies.
 */
public record UpdateFlashcardRequest(
    @NullOrNotBlank
    @Size(max = ValidationLimits.FLASHCARD_TEXT_MAX)
    String question,

    @NullOrNotBlank
    @Size(max = ValidationLimits.FLASHCARD_TEXT_MAX)
    String answer
) {

    public UpdateFlashcardRequest {
        question = RequestText.trimmed(question);
        answer = RequestText.trimmed(answer);
    }

}

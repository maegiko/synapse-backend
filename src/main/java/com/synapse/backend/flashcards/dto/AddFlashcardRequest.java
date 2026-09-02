package com.synapse.backend.flashcards.dto;

import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A card to add to a deck, normalised on construction so the constraints below are checked
 * against the values that are persisted. Both sides carry the same bounds the edit endpoint
 * applies.
 */
public record AddFlashcardRequest(
    @NotBlank
    @Size(max = ValidationLimits.FLASHCARD_TEXT_MAX)
    String question,

    @NotBlank
    @Size(max = ValidationLimits.FLASHCARD_TEXT_MAX)
    String answer
) {

    public AddFlashcardRequest {
        question = RequestText.trimmed(question);
        answer = RequestText.trimmed(answer);
    }

}

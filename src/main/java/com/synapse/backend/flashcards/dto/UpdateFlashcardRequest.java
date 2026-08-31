package com.synapse.backend.flashcards.dto;

/**
 * Optional flashcard fields to update, normalised on construction so the
 * service validates the values that are actually persisted.
 */
public record UpdateFlashcardRequest(
    String question,
    String answer
) {

    public UpdateFlashcardRequest {
        question = question == null ? null : question.trim();
        answer = answer == null ? null : answer.trim();
    }

}

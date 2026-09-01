package com.synapse.backend.quiz.dto;

/**
 * Optional quiz fields to update, normalised on construction so the
 * service validates the values that are actually persisted.
 *
 * <p>{@code pinned} is null when the pin state was not supplied and must be left
 * unchanged, {@code true} to pin the quiz, and {@code false} to unpin it.</p>
 */
public record UpdateQuizRequest(
    String title,
    String description,
    Boolean pinned
) {

    public UpdateQuizRequest {
        title = title == null ? null : title.trim();
        description = description == null ? null : description.trim();
    }

}

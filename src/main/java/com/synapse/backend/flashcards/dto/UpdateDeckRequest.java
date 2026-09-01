package com.synapse.backend.flashcards.dto;

/**
 * Optional flashcard deck fields to update, normalised on construction so the
 * service validates the value that is actually persisted.
 *
 * <p>{@code pinned} is null when the pin state was not supplied and must be left
 * unchanged, {@code true} to pin the deck, and {@code false} to unpin it.</p>
 */
public record UpdateDeckRequest(
    String title,
    Boolean pinned
) {

    public UpdateDeckRequest {
        title = title == null ? null : title.trim();
    }

}

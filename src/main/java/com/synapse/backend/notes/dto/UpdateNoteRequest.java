package com.synapse.backend.notes.dto;

/**
 * Optional note fields to update, normalised on construction so the
 * service validates the values that are actually persisted.
 *
 * <p>{@code pinned} is null when the pin state was not supplied and must be left
 * unchanged, {@code true} to pin the note, and {@code false} to unpin it.</p>
 */
public record UpdateNoteRequest(
    String title,
    String overview,
    Boolean pinned
) {

    public UpdateNoteRequest {
        title = title == null ? null : title.trim();
        overview = overview == null ? null : overview.trim();
    }

}

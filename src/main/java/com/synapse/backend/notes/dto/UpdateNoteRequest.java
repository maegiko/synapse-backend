package com.synapse.backend.notes.dto;

/**
 * Optional note fields to update, normalised on construction so the
 * service validates the values that are actually persisted.
 */
public record UpdateNoteRequest(
    String title,
    String overview
) {

    public UpdateNoteRequest {
        title = title == null ? null : title.trim();
        overview = overview == null ? null : overview.trim();
    }

}

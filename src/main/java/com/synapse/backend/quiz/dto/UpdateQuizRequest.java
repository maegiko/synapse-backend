package com.synapse.backend.quiz.dto;

/**
 * Optional quiz fields to update, normalised on construction so the
 * service validates the values that are actually persisted.
 */
public record UpdateQuizRequest(
    String title,
    String description
) {

    public UpdateQuizRequest {
        title = title == null ? null : title.trim();
        description = description == null ? null : description.trim();
    }

}

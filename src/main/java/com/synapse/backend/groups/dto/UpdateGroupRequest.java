package com.synapse.backend.groups.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional group fields to update, normalised on construction so the
 * constraints below are checked against the values that are persisted.
 */
public record UpdateGroupRequest(
    @Size(max = 100)
    String name,

    @Size(max = 500)
    String description
) {

    public UpdateGroupRequest {
        name = name == null ? null : name.trim();
        description = description == null ? null : description.trim();
    }

}

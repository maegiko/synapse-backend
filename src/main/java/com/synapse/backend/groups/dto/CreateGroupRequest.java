package com.synapse.backend.groups.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new group's name and optional description, normalised on construction so the
 * constraints below are checked against the values that are persisted.
 */
public record CreateGroupRequest(
    @NotBlank
    @Size(max = 100)
    String name,

    @Size(max = 500)
    String description
) {

    public CreateGroupRequest {
        name = name == null ? null : name.trim();
        description = description == null ? null : description.trim();
    }

}

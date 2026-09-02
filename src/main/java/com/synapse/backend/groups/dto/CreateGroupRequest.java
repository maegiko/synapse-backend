package com.synapse.backend.groups.dto;

import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new group's name and optional description, normalised on construction so the constraints
 * below are checked against the values that are persisted.
 */
public record CreateGroupRequest(
    @NotBlank
    @Size(max = ValidationLimits.TITLE_MAX)
    String name,

    @Size(max = ValidationLimits.DESCRIPTION_MAX)
    String description
) {

    public CreateGroupRequest {
        name = RequestText.trimmed(name);
        description = RequestText.trimmed(description);
    }

}

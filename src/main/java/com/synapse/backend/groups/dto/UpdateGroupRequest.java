package com.synapse.backend.groups.dto;

import com.synapse.backend.shared.validation.NullOrNotBlank;
import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.Size;

/**
 * Optional group fields to update, normalised on construction so the constraints below are
 * checked against the values that are persisted. The name carries the same bounds it carries
 * on creation; a blank description clears it.
 */
public record UpdateGroupRequest(
    @NullOrNotBlank
    @Size(max = ValidationLimits.TITLE_MAX)
    String name,

    @Size(max = ValidationLimits.DESCRIPTION_MAX)
    String description
) {

    public UpdateGroupRequest {
        name = RequestText.trimmed(name);
        description = RequestText.trimmed(description);
    }

}

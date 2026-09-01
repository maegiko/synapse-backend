package com.synapse.backend.user.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional profile fields to update, normalised on construction so the
 * constraints below are checked against the values that are persisted.
 *
 * <p>The email address is deliberately not here: changing it needs the new
 * address confirmed first, which is what POST /api/user/email-change starts.</p>
 */
public record UpdateUserDetailsRequest(
    @Size(min = 2, max = 100)
    String fullName,

    @Size(max = 64)
    String timeZone
) {

    public UpdateUserDetailsRequest {
        fullName = fullName == null ? null : fullName.trim();
        timeZone = timeZone == null ? null : timeZone.trim();
    }

}

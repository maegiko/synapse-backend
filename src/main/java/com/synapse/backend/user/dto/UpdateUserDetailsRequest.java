package com.synapse.backend.user.dto;

import com.synapse.backend.shared.validation.FullName;
import com.synapse.backend.shared.validation.NullOrNotBlank;
import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.Size;

/**
 * Optional profile fields to update, normalised on construction so the constraints below are
 * checked against the values that are persisted.
 *
 * <p>The full name carries exactly the rules registration carries: trimmed first, 2 to 100
 * characters, and letters plus the joiners real names use.</p>
 *
 * <p>The email address is deliberately not here: changing it needs the new address confirmed
 * first, which is what POST /api/user/email-change starts.</p>
 */
public record UpdateUserDetailsRequest(
    @NullOrNotBlank
    @FullName
    String fullName,

    @NullOrNotBlank
    @Size(max = ValidationLimits.TIME_ZONE_MAX)
    String timeZone
) {

    public UpdateUserDetailsRequest {
        fullName = RequestText.trimmed(fullName);
        timeZone = RequestText.trimmed(timeZone);
    }

}

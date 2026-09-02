package com.synapse.backend.auth.dto;

import com.synapse.backend.shared.validation.EmailAddress;
import com.synapse.backend.shared.validation.FullName;
import com.synapse.backend.shared.validation.Password;
import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration details, normalised on construction so the constraints below are checked
 * against the values that are actually stored.
 *
 * <p>The full name is letters and the joiners real names use, measured after trimming, which
 * is the same rule and the same order that PATCH /api/user/details applies. The time zone is
 * optional; a client that sends none, or cannot detect one, gets UTC.</p>
 */
public record RegisterRequest(
    @NotBlank
    @FullName
    String fullName,

    @EmailAddress
    String email,

    @Password
    String password,

    @Size(max = ValidationLimits.TIME_ZONE_MAX)
    String timeZone
) {

    public RegisterRequest {
        fullName = RequestText.trimmed(fullName);
        email = RequestText.normalisedEmail(email);
        timeZone = RequestText.trimmed(timeZone);
    }

    /** Registration from a client that sends no time zone, which falls back to UTC. */
    public RegisterRequest(String fullName, String email, String password) {
        this(fullName, email, password, null);
    }

}

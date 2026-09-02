package com.synapse.backend.auth.dto;

import com.synapse.backend.shared.validation.EmailAddress;
import com.synapse.backend.shared.validation.RequestText;

/**
 * The address to email a reset link to, normalised on construction so the constraints below
 * are checked against the value that is actually looked up and rate limited.
 */
public record ForgotPasswordRequest(
    @EmailAddress
    String email
) {

    public ForgotPasswordRequest {
        email = RequestText.normalisedEmail(email);
    }

}

package com.synapse.backend.user.dto;

import com.synapse.backend.shared.validation.EmailAddress;
import com.synapse.backend.shared.validation.RequestText;

/**
 * The address a user proposes to move their account to, normalised on construction so the
 * constraints below are checked against the value that is actually confirmed and persisted.
 */
public record ChangeEmailRequest(
    @EmailAddress
    String email
) {

    public ChangeEmailRequest {
        email = RequestText.normalisedEmail(email);
    }

}

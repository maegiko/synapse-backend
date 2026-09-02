package com.synapse.backend.auth.dto;

import com.synapse.backend.shared.validation.EmailAddress;
import com.synapse.backend.shared.validation.Password;
import com.synapse.backend.shared.validation.RequestText;

/**
 * Sign-in credentials, with the address normalised on construction so it is validated in the
 * form it is looked up in.
 */
public record LoginRequest(
    @EmailAddress
    String email,

    @Password
    String password
) {

    public LoginRequest {
        email = RequestText.normalisedEmail(email);
    }

}

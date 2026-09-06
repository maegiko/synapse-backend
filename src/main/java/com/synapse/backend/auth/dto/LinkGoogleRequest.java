package com.synapse.backend.auth.dto;

import com.synapse.backend.shared.validation.Password;
import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A fresh Google credential and the account's current password. Both are required: linking
 * attaches a second way into an account, so the caller has to prove they hold the Synapse
 * password as well as the Google Account, not merely that they are holding a live session.
 */
public record LinkGoogleRequest(
    @NotBlank
    @Size(max = ValidationLimits.GOOGLE_CREDENTIAL_MAX)
    String credential,

    @Password
    String currentPassword
) {

    public LinkGoogleRequest {
        credential = RequestText.trimmed(credential);
    }

}

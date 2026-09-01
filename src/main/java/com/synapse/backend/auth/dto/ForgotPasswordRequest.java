package com.synapse.backend.auth.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The address to email a reset link to, normalised on construction so the
 * constraints below are checked against the value that is actually looked up and
 * rate limited, matching {@code ChangeEmailRequest}.
 */
public record ForgotPasswordRequest(
    @NotBlank
    @Email
    @Size(max = 255)
    String email
) {

    public ForgotPasswordRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

}

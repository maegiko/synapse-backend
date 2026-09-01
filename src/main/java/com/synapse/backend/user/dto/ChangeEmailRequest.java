package com.synapse.backend.user.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The address a user proposes to move their account to, normalised on
 * construction so the constraints below are checked against the value that is
 * actually confirmed and persisted.
 */
public record ChangeEmailRequest(
    @NotBlank
    @Email
    @Size(max = 255)
    String email
) {

    public ChangeEmailRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

}

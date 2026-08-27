package com.synapse.backend.user.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Optional profile fields to update, normalised on construction so the
 * constraints below are checked against the values that are persisted.
 */
public record UpdateUserDetailsRequest(
    @Size(min = 2, max = 100)
    String fullName,

    @Email
    @Size(max = 255)
    String email
) {

    public UpdateUserDetailsRequest {
        fullName = fullName == null ? null : fullName.trim();
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

}

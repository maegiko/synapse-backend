package com.synapse.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration details. The time zone is optional and trimmed on construction; a
 * client that sends none, or cannot detect one, gets UTC.
 */
public record RegisterRequest(
    @NotBlank
    @Size(min = 2, max = 100)
    String fullName,

    @NotBlank
    @Email
    @Size(max = 255)
    String email,

    @NotBlank
    @Size(min = 8, max = 64)
    String password,

    @Size(max = 64)
    String timeZone
) {

    public RegisterRequest {
        timeZone = timeZone == null ? null : timeZone.trim();
    }

    /** Registration from a client that sends no time zone, which falls back to UTC. */
    public RegisterRequest(String fullName, String email, String password) {
        this(fullName, email, password, null);
    }

}

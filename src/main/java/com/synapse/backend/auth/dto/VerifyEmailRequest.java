package com.synapse.backend.auth.dto;

import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An emailed verification token, trimmed on construction so a token copied with surrounding
 * whitespace still resolves.
 */
public record VerifyEmailRequest(
    @NotBlank
    @Size(max = ValidationLimits.TOKEN_MAX)
    String token
) {

    public VerifyEmailRequest {
        token = RequestText.trimmed(token);
    }

}

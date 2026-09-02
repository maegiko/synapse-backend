package com.synapse.backend.auth.dto;

import com.synapse.backend.shared.validation.Password;
import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An emailed reset token and the password to set with it.
 */
public record ResetPasswordRequest(
    @NotBlank
    @Size(max = ValidationLimits.TOKEN_MAX)
    String token,

    @Password
    String newPassword
) {

    public ResetPasswordRequest {
        token = RequestText.trimmed(token);
    }

}

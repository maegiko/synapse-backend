package com.synapse.backend.auth.dto;

import com.synapse.backend.shared.validation.Password;

/**
 * The current password and its replacement. Both carry the one password rule, so a secret
 * that could never have been registered is refused before it reaches the encoder.
 */
public record ChangePasswordRequest(
    @Password
    String currentPassword,

    @Password
    String newPassword
) {}

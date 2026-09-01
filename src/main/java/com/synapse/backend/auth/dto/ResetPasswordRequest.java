package com.synapse.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank
    @Size(max = 255)
    String token,

    @NotBlank
    @Size(min = 8, max = 64)
    String newPassword
) {}

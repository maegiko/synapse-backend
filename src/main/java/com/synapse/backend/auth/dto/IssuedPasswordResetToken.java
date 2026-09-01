package com.synapse.backend.auth.dto;

import java.time.LocalDateTime;

/**
 * A freshly issued password reset token. The raw token exists only long enough to
 * build the reset link, and the id is the stable non-secret identifier the
 * provider idempotency key is derived from.
 */
public record IssuedPasswordResetToken(
    Long id,
    String rawToken,
    LocalDateTime expiresAt
) {}

package com.synapse.backend.auth.dto;

import java.time.LocalDateTime;

/**
 * A freshly issued verification token. The raw token exists only long enough to
 * build the verification link, and the id is the stable non-secret identifier the
 * provider idempotency key is derived from.
 */
public record IssuedVerificationToken(
    Long id,
    String rawToken,
    LocalDateTime expiresAt
) {}

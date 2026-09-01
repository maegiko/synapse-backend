package com.synapse.backend.user.dto;

import java.time.LocalDateTime;

/**
 * A pending email change. The account keeps its current address until the link
 * sent to {@code pendingEmail} is confirmed.
 */
public record EmailChangeResponse(
    String pendingEmail,
    LocalDateTime expiresAt
) {}

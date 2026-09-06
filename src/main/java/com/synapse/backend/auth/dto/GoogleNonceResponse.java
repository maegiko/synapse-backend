package com.synapse.backend.auth.dto;

/**
 * A one-time nonce for a Google sign-in attempt. The same value is set as a host-only
 * HttpOnly cookie, so the ID token that comes back can only be spent by the browser that
 * asked for it.
 */
public record GoogleNonceResponse(
    String nonce
) {}

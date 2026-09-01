package com.synapse.backend.auth.dto;

/**
 * The answer to a registration request. Registration no longer signs anybody in,
 * so it carries no tokens: the account stays unverified until the emailed link is
 * confirmed.
 */
public record RegisterResponse(
    String email,
    String message
) {}

package com.synapse.backend.auth.dto;

/**
 * Internal DTO pairing a verification response with the raw refresh token for
 * the client cookie, so the controller can set it without leaking the token into
 * the JSON body. The refresh token is null for an email change, which issues no
 * session.
 */
public record VerifyEmailResult(
    VerifyEmailResponse response,
    String refreshToken
) {}

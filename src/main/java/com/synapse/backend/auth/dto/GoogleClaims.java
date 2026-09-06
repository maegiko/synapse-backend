package com.synapse.backend.auth.dto;

/**
 * The claims of a Google ID token that Synapse has verified and is willing to act on.
 *
 * <p>Only reachable through {@code GoogleTokenVerifier}, so a value of this type has already
 * had its signature, issuer, audience, expiry, and nonce checked. {@code subject} is the
 * durable identity; {@code hostedDomain} is Google's {@code hd} claim, which is present when
 * the address belongs to a Google Workspace domain and absent otherwise.</p>
 */
public record GoogleClaims(
    String subject,
    String email,
    boolean emailVerified,
    String name,
    String hostedDomain
) {}

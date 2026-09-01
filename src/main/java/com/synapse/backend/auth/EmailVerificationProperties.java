package com.synapse.backend.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Email verification settings.
 *
 * @param frontendUrl the frontend page verification links point at, which posts the token back to the API.
 * @param tokenTtl how long a verification token stays usable.
 * @param unverifiedRetention how long a never-verified account is kept before cleanup deletes it.
 * @param cleanupInterval how often the cleanup job runs.
 */
@ConfigurationProperties(prefix = "auth.email-verification")
public record EmailVerificationProperties(
    String frontendUrl,
    Duration tokenTtl,
    Duration unverifiedRetention,
    Duration cleanupInterval
) {}

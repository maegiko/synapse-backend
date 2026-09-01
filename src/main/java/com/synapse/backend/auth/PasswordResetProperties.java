package com.synapse.backend.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Password reset settings.
 *
 * <p>A reset link is the strongest credential the application emails: whoever
 * holds it can take the account over. It is therefore short-lived, and shorter
 * than any verification link.</p>
 *
 * @param frontendUrl the frontend page reset links point at, which posts the token back to the API.
 * @param tokenTtl how long a password reset token stays usable.
 */
@ConfigurationProperties(prefix = "auth.password-reset")
public record PasswordResetProperties(
    String frontendUrl,
    Duration tokenTtl
) {}

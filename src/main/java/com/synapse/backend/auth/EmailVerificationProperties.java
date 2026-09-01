package com.synapse.backend.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Email verification settings.
 *
 * <p>The two token lifetimes are deliberately different. Confirming a
 * registration link signs the account in, so that link is a credential and is
 * kept short-lived; an email-change link only moves an address and issues
 * nothing, so it can stay usable for a day.</p>
 *
 * @param frontendUrl the frontend page verification links point at, which posts the token back to the API.
 * @param registrationTokenTtl how long a registration token, which mints a session, stays usable.
 * @param emailChangeTokenTtl how long an email-change token stays usable.
 * @param unverifiedRetention how long a never-verified account is kept before cleanup deletes it.
 * @param cleanupInterval how often the cleanup job runs.
 */
@ConfigurationProperties(prefix = "auth.email-verification")
public record EmailVerificationProperties(
    String frontendUrl,
    Duration registrationTokenTtl,
    Duration emailChangeTokenTtl,
    Duration unverifiedRetention,
    Duration cleanupInterval
) {}

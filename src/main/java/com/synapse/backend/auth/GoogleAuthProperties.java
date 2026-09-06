package com.synapse.backend.auth;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Google Identity Services configuration.
 *
 * <p>{@code clientIds} is the audience an ID token has to be addressed to. It is a list
 * so a deployment can accept more than one OAuth client, such as a web client and a
 * native one, without a second property. There is no client secret: verifying an ID
 * token needs only Google's public keys and the audience.</p>
 */
@ConfigurationProperties(prefix = "auth.google")
public record GoogleAuthProperties(
    List<String> clientIds,
    Duration nonceTtl
) {}

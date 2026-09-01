package com.synapse.backend.email;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resend settings.
 *
 * @param apiKey the Resend API key, supplied by the RESEND_API_KEY environment variable.
 * @param from the verified sender, such as {@code Synapse <no-reply@studysynapse.app>}.
 * @param apiUrl the Resend send-email endpoint.
 * @param connectTimeout how long to wait for the connection.
 * @param readTimeout how long to wait for the response.
 */
@ConfigurationProperties(prefix = "email.resend")
public record ResendProperties(
    String apiKey,
    String from,
    String apiUrl,
    Duration connectTimeout,
    Duration readTimeout
) {}

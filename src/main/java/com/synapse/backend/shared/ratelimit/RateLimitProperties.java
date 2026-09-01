package com.synapse.backend.shared.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ratelimit")
public record RateLimitProperties(
    boolean enabled,
    Limit ai,
    Limit aiDaily,
    Limit login,
    Limit register,
    Limit verificationResend,
    Limit emailChange,
    Limit api
) {

    public record Limit(
        int limit,
        Duration window
    ) {}

}

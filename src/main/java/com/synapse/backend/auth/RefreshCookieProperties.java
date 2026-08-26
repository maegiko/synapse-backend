package com.synapse.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.refresh-cookie")
public record RefreshCookieProperties(
    boolean secure,
    String sameSite
) {}

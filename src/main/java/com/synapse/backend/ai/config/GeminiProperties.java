package com.synapse.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.gemini")
public record GeminiProperties(
    String apiKey
) {}

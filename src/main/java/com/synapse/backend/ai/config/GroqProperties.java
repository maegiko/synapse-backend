package com.synapse.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.groq")
public record GroqProperties(
    String apiKey
) {}

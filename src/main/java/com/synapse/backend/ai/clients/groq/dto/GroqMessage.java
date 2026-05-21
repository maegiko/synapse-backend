package com.synapse.backend.ai.clients.groq.dto;

public record GroqMessage(
    String role,
    String content
) {}


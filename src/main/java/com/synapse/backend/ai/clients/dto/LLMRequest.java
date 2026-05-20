package com.synapse.backend.ai.clients.dto;

public record LLMRequest(
    String model,
    String prompt
) {}

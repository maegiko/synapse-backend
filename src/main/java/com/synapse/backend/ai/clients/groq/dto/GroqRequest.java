package com.synapse.backend.ai.clients.groq.dto;

import java.util.List;

public record GroqRequest(
    String model,
    List<GroqMessage> messages
) {}

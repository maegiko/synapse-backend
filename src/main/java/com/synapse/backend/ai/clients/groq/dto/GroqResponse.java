package com.synapse.backend.ai.clients.groq.dto;

import java.util.List;

public record GroqResponse(
    List<GroqChoice> choices
) {}


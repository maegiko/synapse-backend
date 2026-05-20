package com.synapse.backend.ai.clients.gemini.dto;

import java.util.List;

public record GeminiResponse(
    List<GeminiCandidate> candidates
) {}

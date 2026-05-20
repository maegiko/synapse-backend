package com.synapse.backend.ai.clients.gemini.dto;

import java.util.List;

public record GeminiContent(
    List<GeminiPart> parts
) {}

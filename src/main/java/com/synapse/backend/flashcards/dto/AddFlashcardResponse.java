package com.synapse.backend.flashcards.dto;

import java.time.LocalDateTime;

public record AddFlashcardResponse(
    String id,
    String question,
    String answer,
    LocalDateTime createdAt
) {}

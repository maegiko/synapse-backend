package com.synapse.backend.flashcards.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AddFlashcardResponse(
    UUID id,
    String question,
    String answer,
    LocalDateTime createdAt
) {}

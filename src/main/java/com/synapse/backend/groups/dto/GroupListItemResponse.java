package com.synapse.backend.groups.dto;

import java.time.LocalDateTime;

public record GroupListItemResponse(
    String id,
    String name,
    String description,
    int noteCount,
    int deckCount,
    int quizCount,
    LocalDateTime createdAt
) {}

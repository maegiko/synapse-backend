package com.synapse.backend.groups.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GroupDetailResponse(
    String id,
    String name,
    String description,
    List<GroupContentResponse> notes,
    List<GroupContentResponse> decks,
    List<GroupContentResponse> quizzes,
    LocalDateTime createdAt
) {}

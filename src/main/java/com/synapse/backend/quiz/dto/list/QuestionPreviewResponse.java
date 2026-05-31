package com.synapse.backend.quiz.dto.list;

import java.time.LocalDateTime;

public record QuestionPreviewResponse(
    String id,
    String text,
    LocalDateTime createdAt
) {}

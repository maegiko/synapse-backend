package com.synapse.backend.quiz.dto.list;

import java.time.LocalDateTime;
import java.util.List;

public record QuizListItemResponse(
    String id,
    String title,
    String description,
    List<QuestionPreviewResponse> questions,
    Integer difficulty,
    LocalDateTime createdAt
) {}

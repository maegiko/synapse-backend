package com.synapse.backend.quiz.dto;

import java.time.LocalDateTime;
import java.util.List;

public record QuizResponse(
    String id,
    String title,
    String description,
    List<QuestionResponse> questions,
    LocalDateTime createdAt
) {}

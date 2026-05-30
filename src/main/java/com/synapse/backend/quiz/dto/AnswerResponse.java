package com.synapse.backend.quiz.dto;

import java.time.LocalDateTime;

public record AnswerResponse(
    String id,
    String text,
    boolean correct,
    LocalDateTime createdAt
) {}

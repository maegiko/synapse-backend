package com.synapse.backend.quiz.dto.create;

public record CreateAnswerResponse(
    String id,
    String answer,
    boolean isCorrect
) {}

package com.synapse.backend.quiz.dto.score;

import java.time.LocalDateTime;

public record QuizScoreResponse(
    String publicId,
    String quizId,
    int score,
    int totalQuestions,
    LocalDateTime createdAt
) {}

package com.synapse.backend.quiz.dto.score;

import java.util.List;

public record ListQuizScoreResponse(
    List<QuizScoreResponse> scores
) {}

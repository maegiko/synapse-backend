package com.synapse.backend.quiz.dto;

import java.util.List;

public record ListQuizResponse(
    List<QuizResponse> quizzes
) {}

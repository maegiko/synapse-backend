package com.synapse.backend.quiz.dto.list;

import java.util.List;

public record ListQuizResponse(
    List<QuizListItemResponse> quizzes
) {}

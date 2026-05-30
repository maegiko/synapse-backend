package com.synapse.backend.quiz.dto.generated;

import java.util.List;

public record GeneratedQuizResponse(
    String title,
    String description,
    List<GeneratedQuestionResponse> questions
) {}

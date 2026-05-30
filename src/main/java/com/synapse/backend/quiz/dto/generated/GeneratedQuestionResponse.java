package com.synapse.backend.quiz.dto.generated;

import java.util.List;

import com.synapse.backend.quiz.enums.QuestionType;

public record GeneratedQuestionResponse(
    String questionText,
    QuestionType questionType,
    List<GeneratedAnswerResponse> answers
) {}

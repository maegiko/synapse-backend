package com.synapse.backend.quiz.dto.create;

import java.time.LocalDateTime;
import java.util.List;

import com.synapse.backend.quiz.enums.QuestionType;

public record CreateQuestionResponse(
    String id,
    String question,
    QuestionType questionType,
    List<CreateAnswerResponse> answers,
    LocalDateTime createdAt
) {}

package com.synapse.backend.quiz.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.synapse.backend.quiz.enums.QuestionType;

public record QuestionResponse(
    String id,
    String text,
    QuestionType questionType,
    List<AnswerResponse> answers,
    LocalDateTime createdAt
) {}

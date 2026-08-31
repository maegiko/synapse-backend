package com.synapse.backend.quiz.dto;

import java.util.List;

import com.synapse.backend.quiz.dto.create.CreateQuestionAnswer;
import com.synapse.backend.quiz.enums.QuestionType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Optional quiz question fields to update, normalised on construction so the
 * service validates the values that are actually persisted. When {@code answers}
 * is supplied it is the complete replacement answer set.
 */
public record UpdateQuestionRequest(
    @Size(max = 1000)
    String question,

    QuestionType questionType,

    List<@Valid @NotNull CreateQuestionAnswer> answers
) {

    public UpdateQuestionRequest {
        question = question == null ? null : question.trim();
    }

}

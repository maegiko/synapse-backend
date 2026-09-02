package com.synapse.backend.quiz.dto.create;

import java.util.List;

import com.synapse.backend.quiz.enums.QuestionType;
import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A question to add to a quiz, normalised on construction so the constraints below are
 * checked against the values that are persisted. The question text and the answers carry the
 * same bounds the edit endpoint applies.
 */
public record CreateQuestionRequest(
    @NotBlank
    @Size(max = ValidationLimits.QUESTION_TEXT_MAX)
    String question,

    @NotNull
    QuestionType questionType,

    @NotNull
    List<@Valid @NotNull CreateQuestionAnswer> answers
) {

    public CreateQuestionRequest {
        question = RequestText.trimmed(question);
    }

}

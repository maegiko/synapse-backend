package com.synapse.backend.quiz.dto;

import java.util.List;

import com.synapse.backend.quiz.dto.create.CreateQuestionAnswer;
import com.synapse.backend.quiz.enums.QuestionType;
import com.synapse.backend.shared.validation.NullOrNotBlank;
import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Optional quiz question fields to update, normalised on construction so the constraints
 * below are checked against the values that are persisted. The question text and the answers
 * carry the same bounds the create endpoint applies.
 *
 * <p>When {@code answers} is supplied it is the complete replacement answer set. How many
 * answers a type requires, and that exactly one is correct, is checked against the merged
 * result in the service rather than here, because it depends on the stored question.</p>
 */
public record UpdateQuestionRequest(
    @NullOrNotBlank
    @Size(max = ValidationLimits.QUESTION_TEXT_MAX)
    String question,

    QuestionType questionType,

    List<@Valid @NotNull CreateQuestionAnswer> answers
) {

    public UpdateQuestionRequest {
        question = RequestText.trimmed(question);
    }

}

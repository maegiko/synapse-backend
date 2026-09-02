package com.synapse.backend.quiz.dto.create;

import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One answer option, normalised on construction so the constraints below are checked against
 * the value that is persisted. Used by both the create and the edit endpoint, so an option
 * is bounded the same way whichever one wrote it.
 */
public record CreateQuestionAnswer(
    @NotBlank
    @Size(max = ValidationLimits.ANSWER_TEXT_MAX)
    String answer,

    @NotNull
    Boolean isCorrect
) {

    public CreateQuestionAnswer {
        answer = RequestText.trimmed(answer);
    }

}

package com.synapse.backend.quiz.dto;

import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The note to generate a quiz from, addressed by its public id.
 */
public record GenerateQuizRequest(
    @NotBlank
    @Size(max = ValidationLimits.PUBLIC_ID_MAX)
    String noteId
) {

    public GenerateQuizRequest {
        noteId = RequestText.trimmed(noteId);
    }

}

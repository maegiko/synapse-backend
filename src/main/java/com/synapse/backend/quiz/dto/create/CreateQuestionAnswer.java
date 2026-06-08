package com.synapse.backend.quiz.dto.create;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateQuestionAnswer(
    @NotBlank
    @Size(max = 500)
    String answer,

    @NotNull
    Boolean isCorrect
) {}

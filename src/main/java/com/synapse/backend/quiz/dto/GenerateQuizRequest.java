package com.synapse.backend.quiz.dto;

import jakarta.validation.constraints.NotNull;

public record GenerateQuizRequest(
    @NotNull
    String noteId
) {}

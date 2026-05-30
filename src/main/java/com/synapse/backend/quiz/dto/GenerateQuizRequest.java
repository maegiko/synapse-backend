package com.synapse.backend.quiz.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record GenerateQuizRequest(
    @NotNull
    UUID noteId
) {}

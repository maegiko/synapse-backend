package com.synapse.backend.flashcards.dto;

import jakarta.validation.constraints.NotBlank;

public record AddFlashcardRequest(
    @NotBlank
    String question,

    @NotBlank
    String answer
) {}

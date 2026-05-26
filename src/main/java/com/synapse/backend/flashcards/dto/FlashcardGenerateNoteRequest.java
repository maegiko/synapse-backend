package com.synapse.backend.flashcards.dto;

import jakarta.validation.constraints.NotNull;

public record FlashcardGenerateNoteRequest(
    @NotNull
    Long noteId
) {}

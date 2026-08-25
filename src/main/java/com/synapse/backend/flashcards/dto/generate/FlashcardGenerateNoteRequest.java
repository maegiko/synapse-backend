package com.synapse.backend.flashcards.dto.generate;

import jakarta.validation.constraints.NotNull;

public record FlashcardGenerateNoteRequest(
    @NotNull
    String noteId
) {}

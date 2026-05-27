package com.synapse.backend.flashcards.dto.generate;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record FlashcardGenerateNoteRequest(
    @NotNull
    UUID noteId
) {}

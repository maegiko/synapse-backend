package com.synapse.backend.flashcards.dto.list;

public record FlashcardWithIdResponse(
    String id,
    String title,
    String answer
) {}

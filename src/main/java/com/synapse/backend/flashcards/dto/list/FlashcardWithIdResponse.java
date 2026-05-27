package com.synapse.backend.flashcards.dto.list;

import java.util.UUID;

public record FlashcardWithIdResponse(
    UUID id,
    String title,
    String answer
) {}

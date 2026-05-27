package com.synapse.backend.flashcards.dto;

import java.util.List;

public record FlashcardGenerateResponse(
    Long deckId,
    List<FlashcardResponse> flashcards
) {}

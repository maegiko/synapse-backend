package com.synapse.backend.flashcards.dto.generate;

import java.util.List;

import com.synapse.backend.flashcards.dto.FlashcardResponse;

public record FlashcardGenerateResponse(
    String deckId,
    List<FlashcardResponse> flashcards
) {}

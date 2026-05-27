package com.synapse.backend.flashcards.dto.generate;

import java.util.List;
import java.util.UUID;

import com.synapse.backend.flashcards.dto.FlashcardResponse;

public record FlashcardGenerateResponse(
    UUID deckId,
    List<FlashcardResponse> flashcards
) {}

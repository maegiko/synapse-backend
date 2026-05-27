package com.synapse.backend.flashcards.dto.list;

import java.util.List;
import java.util.UUID;

public record DeckListResponse(
    UUID deckId,
    List<FlashcardWithIdResponse> flashcards
) {}

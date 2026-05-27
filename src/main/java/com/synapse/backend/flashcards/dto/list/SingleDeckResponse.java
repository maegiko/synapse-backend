package com.synapse.backend.flashcards.dto.list;

import java.util.List;
import java.util.UUID;

public record SingleDeckResponse(
    UUID deckId,
    List<FlashcardWithIdResponse> flashcards
) {}

package com.synapse.backend.flashcards.dto.list;

import java.util.List;

public record SingleDeckResponse(
    String deckId,
    List<FlashcardWithIdResponse> flashcards
) {}

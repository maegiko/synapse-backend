package com.synapse.backend.flashcards.dto.list;

import java.util.List;

public record FlashcardListResponse(
    List<SingleDeckResponse> flashcardDecks
) {}

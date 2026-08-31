package com.synapse.backend.flashcards.dto.list;

import java.util.List;

public record FlashcardListResponse(
    List<SingleDeckResponse> flashcardDecks,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {}

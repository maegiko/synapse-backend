package com.synapse.backend.flashcards.dto;

import java.util.List;

public record FlashcardGenerateListResponse(
    List<FlashcardGenerateResponse> flashcards
) {}

package com.synapse.backend.flashcards.dto.generate;

import java.util.List;

import com.synapse.backend.flashcards.dto.FlashcardResponse;

public record FlashcardGenerateListResponse(
    List<FlashcardResponse> flashcards
) {}

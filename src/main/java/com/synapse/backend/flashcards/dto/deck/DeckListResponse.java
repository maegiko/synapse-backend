package com.synapse.backend.flashcards.dto.deck;

import java.util.List;
import java.util.UUID;

import com.synapse.backend.flashcards.dto.FlashcardWithIdResponse;

public record DeckListResponse(
    UUID deckId,
    List<FlashcardWithIdResponse> flashcards
) {}

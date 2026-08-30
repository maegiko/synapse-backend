package com.synapse.backend.flashcards.dto.review;

import java.util.List;

public record ReviewQueueResponse(
    List<ReviewQueueDeckResponse> decks
) {}

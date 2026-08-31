package com.synapse.backend.analytics.dto;

import java.time.LocalDate;

public record FlashcardDayResponse(
    LocalDate date,
    long cardsReviewed,
    long reviewSessions
) {}

package com.synapse.backend.analytics.dto;

import java.time.LocalDate;

/** How many decks fall due on one upcoming day. */
public record DueForecastResponse(
    LocalDate date,
    long deckCount
) {}

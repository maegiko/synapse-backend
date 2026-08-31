package com.synapse.backend.analytics.dto;

import java.time.LocalDate;

/**
 * One calendar day in the user's time zone. Every day of the period gets an entry, including
 * the empty ones, so a client can chart the window without filling gaps itself.
 */
public record DailyActivityResponse(
    LocalDate date,
    long studySeconds,
    long cardsReviewed,
    long deckReviews,
    long quizAttempts
) {}

package com.synapse.backend.analytics.dto;

/**
 * Decks bucketed by how well they are known, from their latest rating and current interval.
 * A deck that has never been reviewed has no latest rating and is in none of these.
 */
public record MasteryCountsResponse(
    /** Last rated AGAIN or HARD. */
    long struggling,
    /** Last rated GOOD or EASY, with an interval under 21 days. */
    long learning,
    /** Last rated GOOD or EASY, with an interval of 21 days or more. */
    long strong
) {}

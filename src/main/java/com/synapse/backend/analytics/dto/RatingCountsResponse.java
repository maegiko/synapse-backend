package com.synapse.backend.analytics.dto;

/** How the period's reviews were rated. */
public record RatingCountsResponse(
    long again,
    long hard,
    long good,
    long easy
) {}

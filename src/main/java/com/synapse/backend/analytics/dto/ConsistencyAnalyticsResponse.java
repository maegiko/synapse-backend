package com.synapse.backend.analytics.dto;

/**
 * How regularly the user studied.
 *
 * <p>{@code currentStreak} and {@code longestStreak} come from the streak history, which counts
 * every qualifying activity including note and quiz generation, so they are not limited to the
 * period. The remaining figures describe the period's own reviews and attempts.</p>
 */
public record ConsistencyAnalyticsResponse(
    int currentStreak,
    int longestStreak,
    int activeDays,
    int inactiveDays,
    double averageSessionsPerActiveDay,
    /** The longest run of consecutive days in the period with no review and no attempt. */
    int longestInactivityGap
) {}

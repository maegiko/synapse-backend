package com.synapse.backend.analytics.dto;

/**
 * The headline figures.
 *
 * <p>{@code cardsReviewed} counts the period only, while {@code lifetimeCardsReviewed} is the
 * user's running total and is never reduced by deletions.</p>
 */
public record AnalyticsOverviewResponse(
    long totalStudySeconds,
    int activeDays,
    int inactiveDays,
    long averageSecondsPerActiveDay,
    long cardsReviewed,
    long lifetimeCardsReviewed,
    long deckReviewSessions,
    long quizAttempts,
    /** Mean quiz result over the period as a percentage, or null when there were no attempts. */
    Double averageQuizPercentage
) {}

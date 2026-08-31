package com.synapse.backend.analytics.dto;

import java.util.List;

/**
 * Flashcard figures. {@code perDay} lists only the days that had a review; the dense
 * timeline with a row for every day is {@code dailyActivity} on the response itself.
 *
 * <p>{@code overdueDecks}, {@code dueTodayDecks}, {@code dueForecast}, and {@code mastery}
 * describe the deck library as it stands now rather than the period, because a schedule is
 * only meaningful in the present.</p>
 */
public record FlashcardAnalyticsResponse(
    long cardsReviewed,
    long reviewSessions,
    List<FlashcardDayResponse> perDay,
    RatingCountsResponse ratings,
    /** {@code (GOOD + EASY) / all ratings} as a 0-1 ratio, or null when nothing was rated. */
    Double retentionRate,
    long overdueDecks,
    long dueTodayDecks,
    List<DueForecastResponse> dueForecast,
    MasteryCountsResponse mastery
) {}

package com.synapse.backend.analytics.dto;

import java.util.List;

/**
 * A user's study analytics over a window of whole calendar days in their own time zone.
 *
 * <p>Counts and totals are zero when nothing happened. Rates and averages that have
 * nothing to average are null rather than zero, because a zero retention rate and no
 * reviews at all are not the same thing.</p>
 */
public record AnalyticsResponse(
    AnalyticsPeriodResponse period,
    AnalyticsOverviewResponse overview,
    FlashcardAnalyticsResponse flashcards,
    QuizAnalyticsResponse quizzes,
    ConsistencyAnalyticsResponse consistency,
    List<DailyActivityResponse> dailyActivity
) {}

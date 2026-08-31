package com.synapse.backend.analytics.dto;

import java.util.List;

/**
 * Quiz figures. {@code perDay} lists only the days that had an attempt; the dense timeline
 * with a row for every day is {@code dailyActivity} on the response itself.
 */
public record QuizAnalyticsResponse(
    long attempts,
    long distinctQuizzesAttempted,
    List<QuizDayResponse> perDay,
    /** Mean result over the period as a percentage, or null when there were no attempts. */
    Double averagePercentage,
    /** Best result over the period as a percentage, or null when there were no attempts. */
    Double bestPercentage,
    /** Mean attempt length in seconds, over the attempts that reported one, or null when none did. */
    Double averageDurationSeconds,
    /** Every attempt in the period, oldest first. */
    List<QuizAttemptResponse> scoreHistory,
    /**
     * Mean of {@code latest percentage - first percentage} across the quizzes attempted at
     * least twice in the period, in percentage points. Positive is improvement. Null when no
     * quiz was attempted twice, since a single attempt shows no trend.
     */
    Double improvement
) {}

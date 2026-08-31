package com.synapse.backend.analytics.dto;

import java.time.LocalDateTime;

/**
 * One saved attempt. {@code totalQuestions} is the snapshot taken when the score was saved,
 * so the percentage stays meaningful even if the quiz was edited afterwards.
 */
public record QuizAttemptResponse(
    String id,
    String quizId,
    String quizTitle,
    int score,
    int totalQuestions,
    double percentage,
    /** How long the attempt took, or null for an attempt whose client did not report it. */
    Integer durationSeconds,
    LocalDateTime createdAt
) {

    /**
     * Used by the score history query, which selects the stored columns and leaves the
     * percentage to be derived here rather than dividing in SQL.
     */
    public QuizAttemptResponse(
        String id,
        String quizId,
        String quizTitle,
        int score,
        int totalQuestions,
        Integer durationSeconds,
        LocalDateTime createdAt
    ) {
        this(
            id,
            quizId,
            quizTitle,
            score,
            totalQuestions,
            totalQuestions == 0 ? 0.0 : score * 100.0 / totalQuestions,
            durationSeconds,
            createdAt
        );
    }

}

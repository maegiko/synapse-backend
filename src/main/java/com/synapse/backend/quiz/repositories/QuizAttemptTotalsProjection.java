package com.synapse.backend.quiz.repositories;

/**
 * Whole-period quiz totals. The averages and the best score are null when the period holds no
 * attempts, and {@code averageDurationSeconds} is also null when no attempt reported one.
 */
public interface QuizAttemptTotalsProjection {

    long getAttempts();

    long getDistinctQuizzes();

    Double getAveragePercentage();

    Double getBestPercentage();

    Double getAverageDurationSeconds();

}

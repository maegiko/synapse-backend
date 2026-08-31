package com.synapse.backend.quiz.repositories;

import java.time.LocalDate;

/** One day's quiz-attempt totals, already grouped by the user's local calendar date. */
public interface DailyQuizAttemptProjection {

    LocalDate getDay();

    long getAttempts();

    long getStudySeconds();

}

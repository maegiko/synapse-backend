package com.synapse.backend.flashcards.repositories;

import java.time.LocalDate;

/** One day's deck-review totals, already grouped by the user's local calendar date. */
public interface DailyDeckReviewProjection {

    LocalDate getDay();

    long getCardsReviewed();

    long getReviewSessions();

    long getStudySeconds();

}

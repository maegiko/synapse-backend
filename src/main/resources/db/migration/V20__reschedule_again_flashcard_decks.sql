UPDATE flashcard_deck
SET interval_days = 0,
    next_review_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date
WHERE last_rating = 'AGAIN';

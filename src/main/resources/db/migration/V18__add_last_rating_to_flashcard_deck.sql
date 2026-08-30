ALTER TABLE flashcard_deck
ADD COLUMN last_rating TEXT NULL;

UPDATE flashcard_deck d
SET last_rating = r.rating
FROM (
    SELECT DISTINCT ON (deck_id) deck_id, rating
    FROM flashcard_deck_review
    ORDER BY deck_id, reviewed_at DESC, id DESC
) r
WHERE r.deck_id = d.id;

UPDATE flashcard_deck
SET last_rating = 'AGAIN'
WHERE review_count > 0 AND last_rating IS NULL;

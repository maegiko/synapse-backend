ALTER TABLE flashcard_deck
ALTER COLUMN next_review_date DROP DEFAULT,
ALTER COLUMN next_review_date DROP NOT NULL;

UPDATE flashcard_deck
SET next_review_date = NULL
WHERE review_count = 0;

UPDATE flashcard_deck
SET last_rating = 'AGAIN'
WHERE review_count > 0;

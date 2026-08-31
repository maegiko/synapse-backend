-- Study time is optional and only ever supplied by the client, so historical rows
-- and clients that omit it stay valid and simply record no duration.
ALTER TABLE flashcard_deck_review
ADD COLUMN duration_seconds INTEGER NULL,
ADD CONSTRAINT check_flashcard_deck_review_duration_seconds
    CHECK (duration_seconds IS NULL OR (duration_seconds >= 0 AND duration_seconds <= 21600));

ALTER TABLE quiz_score
ADD COLUMN duration_seconds INTEGER NULL,
ADD CONSTRAINT check_quiz_score_duration_seconds
    CHECK (duration_seconds IS NULL OR (duration_seconds >= 0 AND duration_seconds <= 21600));

CREATE INDEX idx_flashcard_deck_review_reviewed_at ON flashcard_deck_review(reviewed_at);
CREATE INDEX idx_quiz_score_user_id_created_at ON quiz_score(user_id, created_at);

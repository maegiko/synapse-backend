ALTER TABLE flashcard_deck
ADD COLUMN review_count INTEGER NOT NULL DEFAULT 0,
ADD COLUMN interval_days INTEGER NOT NULL DEFAULT 0,
ADD COLUMN ease_factor NUMERIC(4, 2) NOT NULL DEFAULT 2.50,
ADD COLUMN next_review_date DATE NOT NULL DEFAULT CURRENT_DATE,
ADD COLUMN last_reviewed_at TIMESTAMP NULL,
ADD CONSTRAINT check_flashcard_deck_ease_factor
    CHECK (ease_factor >= 1.30);

CREATE INDEX idx_flashcard_deck_user_id_next_review_date
    ON flashcard_deck(user_id, next_review_date);

CREATE TABLE flashcard_deck_review (
    id BIGSERIAL PRIMARY KEY,
    deck_id BIGINT NOT NULL,
    rating TEXT NOT NULL,
    cards_reviewed INTEGER NOT NULL,
    total_cards INTEGER NOT NULL,
    previous_interval_days INTEGER NOT NULL,
    new_interval_days INTEGER NOT NULL,
    previous_ease_factor NUMERIC(4, 2) NOT NULL,
    new_ease_factor NUMERIC(4, 2) NOT NULL,
    reviewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_flashcard_deck_review_deck
        FOREIGN KEY (deck_id)
        REFERENCES flashcard_deck(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_flashcard_deck_review_deck_id ON flashcard_deck_review(deck_id);

ALTER TABLE app_user
ADD COLUMN total_flashcards_reviewed BIGINT NOT NULL DEFAULT 0;

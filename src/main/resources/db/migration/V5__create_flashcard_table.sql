CREATE TABLE flashcard_deck (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    note_id BIGINT NULL,
    title TEXT NOT NULL,
    source_type TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_flashcard_deck_user
        FOREIGN KEY (user_id)
        REFERENCES app_user(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_flashcard_deck_note
        FOREIGN KEY (note_id)
        REFERENCES note(id)
        ON DELETE SET NULL
);

CREATE TABLE flashcard (
    id BIGSERIAL PRIMARY KEY,
    deck_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_flashcard_deck
        FOREIGN KEY (deck_id)
        REFERENCES flashcard_deck(id)
        ON DELETE CASCADE
);
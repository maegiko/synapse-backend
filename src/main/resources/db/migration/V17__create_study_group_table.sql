CREATE TABLE study_group (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(10) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    name TEXT NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_study_group_user
        FOREIGN KEY (user_id)
        REFERENCES app_user(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_study_group_user_id
ON study_group(user_id);

CREATE INDEX idx_study_group_user_created_at
ON study_group(user_id, created_at DESC);

ALTER TABLE note
ADD COLUMN group_id BIGINT NULL,
ADD CONSTRAINT fk_note_group
    FOREIGN KEY (group_id)
    REFERENCES study_group(id)
    ON DELETE SET NULL;

ALTER TABLE flashcard_deck
ADD COLUMN group_id BIGINT NULL,
ADD CONSTRAINT fk_flashcard_deck_group
    FOREIGN KEY (group_id)
    REFERENCES study_group(id)
    ON DELETE SET NULL;

ALTER TABLE quiz
ADD COLUMN group_id BIGINT NULL,
ADD CONSTRAINT fk_quiz_group
    FOREIGN KEY (group_id)
    REFERENCES study_group(id)
    ON DELETE SET NULL;

CREATE INDEX idx_note_group_id ON note(group_id);
CREATE INDEX idx_flashcard_deck_group_id ON flashcard_deck(group_id);
CREATE INDEX idx_quiz_group_id ON quiz(group_id);

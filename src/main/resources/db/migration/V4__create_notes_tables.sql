CREATE TABLE note (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title TEXT NOT NULL,
    overview TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_note_user
        FOREIGN KEY (user_id)
        REFERENCES app_user(id)
        ON DELETE CASCADE
);

CREATE TABLE note_keypoint (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL,
    position INTEGER NOT NULL,
    keypoint TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_point_note
        FOREIGN KEY (note_id)
        REFERENCES note(id)
        ON DELETE CASCADE
);

CREATE TABLE note_concept (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL,
    position INTEGER NOT NULL,
    name TEXT NOT NULL,
    explanation TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_concept_note
        FOREIGN KEY (note_id)
        REFERENCES note(id)
        ON DELETE CASCADE
);

CREATE TABLE note_important_term (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL,
    position INTEGER NOT NULL,
    term TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_term_note
        FOREIGN KEY (note_id)
        REFERENCES note(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_note_user_id
ON note(user_id);

CREATE INDEX idx_note_user_created_at
ON note(user_id, created_at DESC);

CREATE INDEX idx_keypoint_note_id
ON note_keypoint(note_id);

CREATE INDEX idx_concept_note_id
ON note_concept(note_id);

CREATE INDEX idx_important_term_note_id
ON note_important_term(note_id);
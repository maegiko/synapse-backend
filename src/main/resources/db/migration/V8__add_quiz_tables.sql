CREATE TABLE quiz (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(10) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    note_id BIGINT NULL,
    title TEXT NOT NULL,
    description TEXT NULL,
    source_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_quiz_user
        FOREIGN KEY (user_id)
        REFERENCES app_user(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_quiz_note
        FOREIGN KEY(note_id)
        REFERENCES note(id)
        ON DELETE SET NULL
);

CREATE TABLE quiz_question (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(10) NOT NULL UNIQUE,
    quiz_id BIGINT NOT NULL,
    question_text TEXT NOT NULL,
    question_type VARCHAR(50) NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_quiz_question_quiz
        FOREIGN KEY (quiz_id)
        REFERENCES quiz(id)
        ON DELETE CASCADE
);

CREATE TABLE quiz_answer (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(10) NOT NULL UNIQUE,
    question_id BIGINT NOT NULL,
    answer_text TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_quiz_answer_quiz
        FOREIGN KEY (question_id)
        REFERENCES quiz_question(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_quiz_user_id
ON quiz(user_id);

CREATE INDEX idx_quiz_user_created_at
ON quiz(user_id, created_at DESC);

CREATE INDEX idx_quiz_question_quiz_id
ON quiz_question(quiz_id);

CREATE INDEX idx_quiz_answer_question_id
ON quiz_answer(question_id);



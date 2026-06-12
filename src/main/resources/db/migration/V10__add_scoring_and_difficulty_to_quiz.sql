ALTER TABLE quiz
ADD COLUMN difficulty INTEGER,
ADD CONSTRAINT check_quiz_difficulty
    CHECK (difficulty BETWEEN 1 AND 5);

CREATE TABLE quiz_score (
    id BIGSERIAL PRIMARY KEY,
    quiz_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT check_score_not_negative
        CHECK (score >= 0),

    CONSTRAINT fk_quiz_score_quiz
        FOREIGN KEY (quiz_id)
        REFERENCES quiz(id)
        ON DELETE CASCADE,
    
    CONSTRAINT fk_quiz_score_user
        FOREIGN KEY (user_id)
        REFERENCES app_user(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_quiz_score_quiz_id ON quiz_score(quiz_id);
CREATE INDEX idx_quiz_score_user_id ON quiz_score(user_id);
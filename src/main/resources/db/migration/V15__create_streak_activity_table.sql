CREATE TABLE streak_activity (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_streak_activity_user_id_activity_date
        UNIQUE (user_id, activity_date),

    CONSTRAINT fk_streak_activity_user
        FOREIGN KEY (user_id)
        REFERENCES app_user(id)
        ON DELETE CASCADE
);

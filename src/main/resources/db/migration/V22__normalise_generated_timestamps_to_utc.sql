UPDATE flashcard_deck
SET created_at = created_at AT TIME ZONE 'Australia/Sydney' AT TIME ZONE 'UTC',
    updated_at = updated_at AT TIME ZONE 'Australia/Sydney' AT TIME ZONE 'UTC';

UPDATE flashcard
SET created_at = created_at AT TIME ZONE 'Australia/Sydney' AT TIME ZONE 'UTC',
    updated_at = updated_at AT TIME ZONE 'Australia/Sydney' AT TIME ZONE 'UTC';

UPDATE quiz
SET created_at = created_at AT TIME ZONE 'Australia/Sydney' AT TIME ZONE 'UTC',
    updated_at = updated_at AT TIME ZONE 'Australia/Sydney' AT TIME ZONE 'UTC';

UPDATE quiz_question
SET created_at = created_at AT TIME ZONE 'Australia/Sydney' AT TIME ZONE 'UTC',
    updated_at = updated_at AT TIME ZONE 'Australia/Sydney' AT TIME ZONE 'UTC';

UPDATE quiz_answer
SET created_at = created_at AT TIME ZONE 'Australia/Sydney' AT TIME ZONE 'UTC',
    updated_at = updated_at AT TIME ZONE 'Australia/Sydney' AT TIME ZONE 'UTC';

UPDATE study_group
SET created_at = created_at AT TIME ZONE 'Australia/Sydney' AT TIME ZONE 'UTC',
    updated_at = updated_at AT TIME ZONE 'Australia/Sydney' AT TIME ZONE 'UTC';

UPDATE streak_activity
SET created_at = created_at AT TIME ZONE 'Australia/Sydney' AT TIME ZONE 'UTC';

UPDATE refresh_token
SET expires_at = expires_at AT TIME ZONE 'Australia/Sydney' AT TIME ZONE 'UTC',
    created_at = created_at AT TIME ZONE 'Australia/Sydney' AT TIME ZONE 'UTC';

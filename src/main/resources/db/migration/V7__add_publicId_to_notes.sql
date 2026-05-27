CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE note
ADD COLUMN public_id UUID;

UPDATE note
SET public_id = gen_random_uuid()
WHERE public_id IS NULL;

ALTER TABLE note
ALTER COLUMN public_id SET NOT NULL;

ALTER TABLE note
ADD CONSTRAINT uq_note_public_id UNIQUE (public_id);
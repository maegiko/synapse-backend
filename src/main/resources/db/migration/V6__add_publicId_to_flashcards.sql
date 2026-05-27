CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE flashcard_deck
ADD COLUMN public_id UUID;

UPDATE flashcard_deck
SET public_id = gen_random_uuid()
WHERE public_id IS NULL;

ALTER TABLE flashcard_deck
ALTER COLUMN public_id SET NOT NULL;

ALTER TABLE flashcard_deck
ADD CONSTRAINT uq_flashcard_deck_public_id UNIQUE (public_id);


ALTER TABLE flashcard
ADD COLUMN public_id UUID;

UPDATE flashcard
SET public_id = gen_random_uuid()
WHERE public_id IS NULL;

ALTER TABLE flashcard
ALTER COLUMN public_id SET NOT NULL;

ALTER TABLE flashcard
ADD CONSTRAINT uq_flashcard_public_id UNIQUE (public_id);
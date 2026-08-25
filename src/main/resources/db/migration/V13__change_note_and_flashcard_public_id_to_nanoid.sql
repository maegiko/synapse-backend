ALTER TABLE note
DROP CONSTRAINT uq_note_public_id;

ALTER TABLE note
DROP COLUMN public_id;

ALTER TABLE note
ADD COLUMN public_id VARCHAR(10);

UPDATE note
SET public_id = substr(md5(random()::text), 1, 10)
WHERE public_id IS NULL;

ALTER TABLE note
ALTER COLUMN public_id SET NOT NULL;

ALTER TABLE note
ADD CONSTRAINT uq_note_public_id UNIQUE (public_id);


ALTER TABLE flashcard_deck
DROP CONSTRAINT uq_flashcard_deck_public_id;

ALTER TABLE flashcard_deck
DROP COLUMN public_id;

ALTER TABLE flashcard_deck
ADD COLUMN public_id VARCHAR(10);

UPDATE flashcard_deck
SET public_id = substr(md5(random()::text), 1, 10)
WHERE public_id IS NULL;

ALTER TABLE flashcard_deck
ALTER COLUMN public_id SET NOT NULL;

ALTER TABLE flashcard_deck
ADD CONSTRAINT uq_flashcard_deck_public_id UNIQUE (public_id);


ALTER TABLE flashcard
DROP CONSTRAINT uq_flashcard_public_id;

ALTER TABLE flashcard
DROP COLUMN public_id;

ALTER TABLE flashcard
ADD COLUMN public_id VARCHAR(10);

UPDATE flashcard
SET public_id = substr(md5(random()::text), 1, 10)
WHERE public_id IS NULL;

ALTER TABLE flashcard
ALTER COLUMN public_id SET NOT NULL;

ALTER TABLE flashcard
ADD CONSTRAINT uq_flashcard_public_id UNIQUE (public_id);

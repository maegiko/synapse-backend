-- Pinning lets a user float a note, deck, or quiz to the top of its library list.
-- Everything that exists today keeps its normal newest-first position until the
-- user pins it, and every newly created resource starts unpinned.
ALTER TABLE note
ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE flashcard_deck
ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE quiz
ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;

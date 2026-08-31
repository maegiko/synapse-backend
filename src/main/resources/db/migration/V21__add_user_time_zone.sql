ALTER TABLE app_user
ADD COLUMN time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC';

-- Every account that exists today was created while the app assumed one calendar,
-- so they keep the calendar their streaks and schedules were already built on.
-- New users fall back to the column default until registration supplies a zone.
UPDATE app_user
SET time_zone = 'Australia/Sydney';

-- Scheduled decks were dated from the UTC day of their last review. Redate them
-- from the owner's local day instead, so a deck reviewed after local midnight is
-- not due a day early. Never-reviewed decks have no next_review_date and stay
-- unscheduled, and a deck with no last_reviewed_at has nothing to recalculate from.
UPDATE flashcard_deck d
SET next_review_date =
    (d.last_reviewed_at AT TIME ZONE 'UTC' AT TIME ZONE u.time_zone)::date + d.interval_days
FROM app_user u
WHERE u.id = d.user_id
  AND d.next_review_date IS NOT NULL
  AND d.last_reviewed_at IS NOT NULL;

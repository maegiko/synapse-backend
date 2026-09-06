-- Google sign-in attaches a Google identity to a Synapse account rather than
-- replacing the account system. Synapse still owns the session, so an account can
-- now be reached by a password, by Google, or by both.
--
-- An account created by Google has never chosen a password, so password_hash stops
-- being mandatory. The check constraint below is what keeps a row from losing every
-- way in: unlinking Google from a passwordless account, or clearing the password of
-- an account with no Google identity, is refused by the database rather than only by
-- the service that asks for it.
ALTER TABLE app_user
ALTER COLUMN password_hash DROP NOT NULL;

-- Google's subject claim is the durable identity, not the email address: a Google
-- Account keeps its subject when its owner changes their Google email, and a Synapse
-- user can edit their Synapse email independently. The column is unique so one Google
-- Account can never reach two Synapse accounts, which is also what makes a concurrent
-- first login safe.
ALTER TABLE app_user
ADD COLUMN google_subject VARCHAR(255) NULL;

ALTER TABLE app_user
ADD CONSTRAINT uq_app_user_google_subject
    UNIQUE (google_subject);

ALTER TABLE app_user
ADD CONSTRAINT check_app_user_login_method
    CHECK (password_hash IS NOT NULL OR google_subject IS NOT NULL);

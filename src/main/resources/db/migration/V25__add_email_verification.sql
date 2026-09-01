-- Registration now waits for the user to confirm their address, so an account is
-- only usable once email_verified_at is set. Every account that exists today was
-- created before verification existed and has already been used, so they are all
-- backfilled as verified. New rows are inserted with NULL and stay unverified
-- until their registration token is consumed.
ALTER TABLE app_user
ADD COLUMN email_verified_at TIMESTAMP NULL;

UPDATE app_user
SET email_verified_at = CURRENT_TIMESTAMP;

-- Verification tokens follow the refresh token security model: only the SHA-256
-- hash of the raw token is stored, the hash is unique, and the row records why it
-- was issued, which address it confirms, and whether it has been consumed or
-- invalidated by a replacement.
CREATE TABLE email_verification_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    invalidated_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_email_verification_token_token_hash
        UNIQUE (token_hash),

    CONSTRAINT check_email_verification_token_purpose
        CHECK (purpose IN ('REGISTRATION', 'EMAIL_CHANGE')),

    CONSTRAINT fk_email_verification_token_user
        FOREIGN KEY (user_id)
        REFERENCES app_user(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_email_verification_token_user_id_purpose
ON email_verification_token(user_id, purpose);

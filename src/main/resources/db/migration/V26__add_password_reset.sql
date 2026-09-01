-- Password resets get their own table rather than another purpose on
-- email_verification_token, so the two kinds of emailed link can never be
-- confused: a verification link can never set a password, and a reset link can
-- never confirm an address.
--
-- The row follows the refresh token and verification token security model. Only
-- the SHA-256 hash of the raw token is stored, the hash is unique, and the row
-- records whether it has been consumed or invalidated by a newer request.
CREATE TABLE password_reset_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    invalidated_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_password_reset_token_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_password_reset_token_user
        FOREIGN KEY (user_id)
        REFERENCES app_user(id)
        ON DELETE CASCADE
);

-- Issuing a reset invalidates the user's previous active tokens, which is the
-- only user-scoped query this table has.
CREATE INDEX idx_password_reset_token_user_id ON password_reset_token(user_id);

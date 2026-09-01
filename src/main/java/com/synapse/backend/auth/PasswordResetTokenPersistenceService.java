package com.synapse.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.synapse.backend.auth.dto.IssuedPasswordResetToken;
import com.synapse.backend.auth.entities.PasswordResetToken;
import com.synapse.backend.auth.exceptions.InvalidPasswordResetTokenException;
import com.synapse.backend.auth.repositories.PasswordResetTokenRepository;

import jakarta.transaction.Transactional;

@Service
public class PasswordResetTokenPersistenceService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetProperties passwordResetProperties;

    public PasswordResetTokenPersistenceService(
        PasswordResetTokenRepository passwordResetTokenRepository,
        PasswordResetProperties passwordResetProperties
    ) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordResetProperties = passwordResetProperties;
    }

    /**
     * Issues a password reset token for a user and invalidates their previous
     * active ones.
     *
     * <p>Only a SHA-256 hash of the token is stored, so a database leak does not
     * expose usable reset links. The raw token is returned once to the caller,
     * which uses it to build the link and then discards it.</p>
     *
     * @param userId the internal id of the user the token belongs to.
     * @return the saved token's id, the raw token, and when it expires.
     */
    @Transactional
    public IssuedPasswordResetToken issueToken(Long userId) {
        passwordResetTokenRepository.invalidateActiveByUserId(userId);

        byte[] tokenBytes = new byte[TOKEN_BYTES];

        SECURE_RANDOM.nextBytes(tokenBytes);

        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plus(passwordResetProperties.tokenTtl());

        PasswordResetToken saved = passwordResetTokenRepository.save(
            new PasswordResetToken(userId, hashToken(token), expiresAt)
        );

        return new IssuedPasswordResetToken(saved.getId(), token, saved.getExpiresAt());
    }

    /**
     * Consumes a valid password reset token so it cannot be used again.
     *
     * <p>Consumption is a single conditional update that also checks expiry and
     * invalidation, so concurrent resets with the same token race for one database
     * row and only the first one is allowed to consume it.</p>
     *
     * @param token the raw token presented by the client.
     * @return the consumed token, carrying the user whose password may now be set.
     * @throws InvalidPasswordResetTokenException if the token is unknown, expired, invalidated, or consumed.
     */
    @Transactional
    public PasswordResetToken consumeToken(String token) {
        String tokenHash = hashToken(token);

        if (passwordResetTokenRepository.consumeActiveByTokenHash(tokenHash) == 0)
            throw new InvalidPasswordResetTokenException();

        return passwordResetTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new InvalidPasswordResetTokenException());
    }

    /**
     * Deletes reset tokens that expired before a cutoff.
     *
     * <p>Expired tokens can never be consumed, so nothing depends on keeping
     * them.</p>
     *
     * @param cutoff the UTC time tokens must have expired before.
     * @return how many tokens were deleted.
     */
    @Transactional
    public long deleteExpiredTokens(LocalDateTime cutoff) {
        return passwordResetTokenRepository.deleteExpiredBefore(cutoff);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required to hash password reset tokens", ex);
        }
    }

}

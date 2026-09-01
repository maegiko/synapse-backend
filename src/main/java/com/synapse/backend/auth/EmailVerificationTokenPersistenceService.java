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

import com.synapse.backend.auth.dto.IssuedVerificationToken;
import com.synapse.backend.auth.entities.EmailVerificationToken;
import com.synapse.backend.auth.enums.EmailVerificationPurpose;
import com.synapse.backend.auth.exceptions.InvalidVerificationTokenException;
import com.synapse.backend.auth.repositories.EmailVerificationTokenRepository;

import jakarta.transaction.Transactional;

@Service
public class EmailVerificationTokenPersistenceService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final EmailVerificationProperties emailVerificationProperties;

    public EmailVerificationTokenPersistenceService(
        EmailVerificationTokenRepository emailVerificationTokenRepository,
        EmailVerificationProperties emailVerificationProperties
    ) {
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.emailVerificationProperties = emailVerificationProperties;
    }

    /**
     * Issues a verification token for a user and invalidates their previous active
     * tokens issued for the same purpose.
     *
     * <p>Only a SHA-256 hash of the token is stored, so a database leak does not
     * expose usable verification links. The raw token is returned once to the
     * caller, which uses it to build the link and then discards it.</p>
     *
     * @param userId the internal id of the user the token belongs to.
     * @param email the address the token confirms, which is the new address for an email change.
     * @param purpose why the token was issued.
     * @return the saved token's id, the raw token, and when it expires.
     */
    @Transactional
    public IssuedVerificationToken issueToken(Long userId, String email, EmailVerificationPurpose purpose) {
        emailVerificationTokenRepository.invalidateActiveByUserIdAndPurpose(userId, purpose);

        byte[] tokenBytes = new byte[TOKEN_BYTES];

        SECURE_RANDOM.nextBytes(tokenBytes);

        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plus(emailVerificationProperties.tokenTtl());

        EmailVerificationToken saved = emailVerificationTokenRepository.save(
            new EmailVerificationToken(userId, email, purpose, hashToken(token), expiresAt)
        );

        return new IssuedVerificationToken(saved.getId(), token, saved.getExpiresAt());
    }

    /**
     * Consumes a valid verification token so it cannot be used again.
     *
     * <p>Consumption is a single conditional update that also checks expiry and
     * invalidation, so concurrent confirmations with the same token race for one
     * database row and only the first one is allowed to consume it.</p>
     *
     * @param token the raw token presented by the client.
     * @return the consumed token, carrying the user, target address, and purpose.
     * @throws InvalidVerificationTokenException if the token is unknown, expired, invalidated, or consumed.
     */
    @Transactional
    public EmailVerificationToken consumeToken(String token) {
        String tokenHash = hashToken(token);

        if (emailVerificationTokenRepository.consumeActiveByTokenHash(tokenHash) == 0)
            throw new InvalidVerificationTokenException();

        return emailVerificationTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new InvalidVerificationTokenException());
    }

    /**
     * Deletes verification tokens that expired before a cutoff.
     *
     * <p>Expired tokens can never be consumed, so nothing depends on keeping
     * them.</p>
     *
     * @param cutoff the UTC time tokens must have expired before.
     * @return how many tokens were deleted.
     */
    @Transactional
    public long deleteExpiredTokens(LocalDateTime cutoff) {
        return emailVerificationTokenRepository.deleteExpiredBefore(cutoff);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required to hash verification tokens", ex);
        }
    }

}

package com.synapse.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.synapse.backend.auth.entities.RefreshToken;
import com.synapse.backend.auth.exceptions.InvalidRefreshTokenException;
import com.synapse.backend.auth.repositories.RefreshTokenRepository;
import com.synapse.backend.security.jwt.JwtProperties;

import jakarta.transaction.Transactional;

@Service
public class RefreshTokenPersistenceService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public RefreshTokenPersistenceService(
        RefreshTokenRepository refreshTokenRepository,
        JwtProperties jwtProperties
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Issues a new refresh token for a user.
     *
     * <p>Only a SHA-256 hash of the token is stored, so a database leak does not
     * expose usable refresh tokens. The raw token is returned once to the caller.</p>
     *
     * @param userId the internal id of the user the token belongs to.
     * @return the raw refresh token to send to the client.
     */
    public String issueRefreshToken(Long userId) {
        byte[] tokenBytes = new byte[TOKEN_BYTES];

        SECURE_RANDOM.nextBytes(tokenBytes);

        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        LocalDateTime expiresAt = LocalDateTime.now().plus(jwtProperties.refreshTokenTtl());

        refreshTokenRepository.save(new RefreshToken(userId, hashToken(token), expiresAt));

        return token;
    }

    /**
     * Revokes a valid refresh token so it cannot be used again.
     *
     * <p>Used when rotating a refresh token, so the presented token is consumed
     * before a replacement is issued.</p>
     *
     * @param token the raw refresh token presented by the client.
     * @return the internal id of the user the token belonged to.
     * @throws InvalidRefreshTokenException if the token is unknown, revoked, or expired.
     */
    @Transactional
    public Long consumeRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashToken(token))
            .orElseThrow(() -> new InvalidRefreshTokenException());

        if (refreshToken.getRevokedAt() != null || refreshToken.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new InvalidRefreshTokenException();

        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        return refreshToken.getUserId();
    }

    /**
     * Revokes a refresh token if it is still active.
     *
     * <p>Unknown and already revoked tokens are ignored so logout stays idempotent.</p>
     *
     * @param token the raw refresh token presented by the client.
     */
    @Transactional
    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByTokenHash(hashToken(token))
            .filter(refreshToken -> refreshToken.getRevokedAt() == null)
            .ifPresent(refreshToken -> {
                refreshToken.revoke();
                refreshTokenRepository.save(refreshToken);
            });
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required to hash refresh tokens", ex);
        }
    }

}

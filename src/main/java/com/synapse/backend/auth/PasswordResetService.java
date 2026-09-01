package com.synapse.backend.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.synapse.backend.auth.dto.IssuedPasswordResetToken;
import com.synapse.backend.auth.entities.PasswordResetToken;
import com.synapse.backend.auth.exceptions.InvalidPasswordResetTokenException;
import com.synapse.backend.email.EmailClient;
import com.synapse.backend.email.dto.EmailMessage;
import com.synapse.backend.email.exceptions.EmailProviderException;
import com.synapse.backend.shared.ratelimit.RateLimitProperties;
import com.synapse.backend.shared.ratelimit.RateLimitService;
import com.synapse.backend.user.User;
import com.synapse.backend.user.UserRepository;

import jakarta.transaction.Transactional;

/**
 * Issues, sends, and consumes the emailed tokens that let somebody who has lost
 * their password set a new one.
 *
 * <p>A reset token is deliberately not an {@code EmailVerificationToken} with
 * another purpose. The two live in separate tables and are consumed by separate
 * endpoints, so a verification link can never set a password and a reset link can
 * never confirm an address.</p>
 *
 * <p>The token is saved before its email is sent, and the provider call is not
 * part of that transaction, following the verification flow. Unlike verification,
 * a failed send is swallowed: the request answers the same way whatever happens,
 * because the response must never describe an account.</p>
 */
@Service
public class PasswordResetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetService.class);

    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String IDEMPOTENCY_KEY_PREFIX = "password-reset-";

    private final PasswordResetTokenPersistenceService passwordResetTokenPersistenceService;
    private final PasswordResetProperties passwordResetProperties;
    private final UserRepository userRepository;
    private final EmailClient emailClient;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenPersistenceService refreshTokenPersistenceService;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;

    public PasswordResetService(
        PasswordResetTokenPersistenceService passwordResetTokenPersistenceService,
        PasswordResetProperties passwordResetProperties,
        UserRepository userRepository,
        EmailClient emailClient,
        PasswordEncoder passwordEncoder,
        RefreshTokenPersistenceService refreshTokenPersistenceService,
        RateLimitService rateLimitService,
        RateLimitProperties rateLimitProperties
    ) {
        this.passwordResetTokenPersistenceService = passwordResetTokenPersistenceService;
        this.passwordResetProperties = passwordResetProperties;
        this.userRepository = userRepository;
        this.emailClient = emailClient;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenPersistenceService = refreshTokenPersistenceService;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * Emails a reset link to an address, if it belongs to an account that could
     * use one.
     *
     * <p>Nothing about the outcome reaches the caller. An unknown address, an
     * address whose account has never confirmed itself, and a live account all
     * return the same way, and so does a provider failure, so the endpoint cannot
     * be used to discover who has an account. Only a verified account is sent a
     * link, because an unverified one has never proven it owns the address and
     * must finish registration instead.</p>
     *
     * <p>The limits are checked before the account is looked up, so an address
     * that does not exist costs the same as one that does.</p>
     *
     * @param email the proposed address, already trimmed and lowercased by the request record.
     * @param clientIp the address the request came from.
     * @throws RateLimitExceededException if the email or the client address has asked too many times.
     */
    public void requestReset(String email, String clientIp) {
        rateLimitService.check("password-reset-email:" + email, rateLimitProperties.passwordReset());
        rateLimitService.check("password-reset-ip:" + clientIp, rateLimitProperties.passwordReset());

        Optional<User> user = userRepository.findByEmail(email);

        if (user.isEmpty() || !user.get().isEmailVerified())
            return;

        IssuedPasswordResetToken token = passwordResetTokenPersistenceService.issueToken(user.get().getId());

        try {
            emailClient.send(resetMessage(email, token));
        } catch (EmailProviderException ex) {
            // The response cannot change shape for a provider failure without telling the
            // caller that this address has an account, so the failure is logged instead.
            // The exception carries only the generic provider message, never the key,
            // the address, or the link.
            LOGGER.warn("A password reset email could not be sent", ex);
        }
    }

    /**
     * Sets a new password from a reset link and ends every session of that user.
     *
     * <p>The token is consumed first, so a replayed link fails like an unknown
     * one, and consumption, the password write, and the revocation of the user's
     * refresh tokens are one transaction: if either write fails, the link stays
     * usable rather than being burnt on a reset that never happened.</p>
     *
     * <p>The caller is not signed in by this. Access tokens already issued cannot
     * be revoked and stay valid until they expire, which is why the refresh tokens
     * are revoked here and the caller's refresh cookie is cleared by the
     * controller.</p>
     *
     * @param rawToken the raw token the frontend read from the link.
     * @param newPassword the validated new password.
     * @throws InvalidPasswordResetTokenException if the token is unknown, expired, invalidated, or used.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenPersistenceService.consumeToken(rawToken);
        User user = userRepository.findById(token.getUserId())
            .orElseThrow(() -> new InvalidPasswordResetTokenException());

        user.updatePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        refreshTokenPersistenceService.revokeAllRefreshTokens(user.getId());
    }

    private EmailMessage resetMessage(String email, IssuedPasswordResetToken token) {
        String link = resetLink(token.rawToken());
        String expiry = expiry(token.expiresAt());

        String text = """
            Somebody asked to reset the password of the Synapse account for this address.

            Set a new password here:

            %s

            This link can only be used once and expires at %s. Opening it signs no one in
            on its own, and your current password keeps working until a new one is set.

            If you did not ask for this, ignore this email. Your password has not changed.
            """.formatted(link, expiry);

        String html = """
            <p>Somebody asked to reset the password of the Synapse account for this address.</p>
            <p>Set a new password here:</p>
            <p><a href="%s">Choose a new password</a></p>
            <p>This link can only be used once and expires at %s. Opening it signs no one in
            on its own, and your current password keeps working until a new one is set.</p>
            <p>If you did not ask for this, ignore this email. Your password has not changed.</p>
            """.formatted(link, expiry);

        return new EmailMessage(email, "Reset your Synapse password", text, html, idempotencyKey(token));
    }

    private String resetLink(String rawToken) {
        return passwordResetProperties.frontendUrl()
            + "?token="
            + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private String expiry(LocalDateTime expiresAt) {
        return EXPIRY_FORMAT.format(expiresAt) + " UTC";
    }

    private String idempotencyKey(IssuedPasswordResetToken token) {
        return IDEMPOTENCY_KEY_PREFIX + token.id();
    }

}

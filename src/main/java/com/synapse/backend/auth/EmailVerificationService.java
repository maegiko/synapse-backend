package com.synapse.backend.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.synapse.backend.auth.dto.IssuedVerificationToken;
import com.synapse.backend.auth.dto.VerifyEmailResponse;
import com.synapse.backend.auth.dto.VerifyEmailResult;
import com.synapse.backend.auth.entities.EmailVerificationToken;
import com.synapse.backend.auth.enums.EmailVerificationPurpose;
import com.synapse.backend.auth.exceptions.EmailAlreadyExistsException;
import com.synapse.backend.auth.exceptions.InvalidVerificationTokenException;
import com.synapse.backend.email.EmailClient;
import com.synapse.backend.email.dto.EmailMessage;
import com.synapse.backend.security.jwt.JwtService;
import com.synapse.backend.shared.ratelimit.RateLimitProperties;
import com.synapse.backend.shared.ratelimit.RateLimitService;
import com.synapse.backend.user.User;
import com.synapse.backend.user.UserRepository;

import jakarta.transaction.Transactional;

/**
 * Issues, sends, and confirms the emailed tokens that prove somebody owns an
 * address.
 *
 * <p>A token is always saved before its email is sent, so a provider failure
 * leaves an account that the resend endpoint can recover instead of an account
 * that can never be used. The database work and the provider call are
 * deliberately not one transaction.</p>
 */
@Service
public class EmailVerificationService {

    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String IDEMPOTENCY_KEY_PREFIX = "email-verification-";

    private final EmailVerificationTokenPersistenceService emailVerificationTokenPersistenceService;
    private final EmailVerificationProperties emailVerificationProperties;
    private final UserRepository userRepository;
    private final EmailClient emailClient;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final JwtService jwtService;
    private final RefreshTokenPersistenceService refreshTokenPersistenceService;

    public EmailVerificationService(
        EmailVerificationTokenPersistenceService emailVerificationTokenPersistenceService,
        EmailVerificationProperties emailVerificationProperties,
        UserRepository userRepository,
        EmailClient emailClient,
        RateLimitService rateLimitService,
        RateLimitProperties rateLimitProperties,
        JwtService jwtService,
        RefreshTokenPersistenceService refreshTokenPersistenceService
    ) {
        this.emailVerificationTokenPersistenceService = emailVerificationTokenPersistenceService;
        this.emailVerificationProperties = emailVerificationProperties;
        this.userRepository = userRepository;
        this.emailClient = emailClient;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.jwtService = jwtService;
        this.refreshTokenPersistenceService = refreshTokenPersistenceService;
    }

    /**
     * Issues a registration token for a newly created account and emails its link.
     *
     * <p>Any previous registration token of that user is invalidated, so only the
     * newest link works.</p>
     *
     * @param user the unverified user to send the link to.
     * @throws EmailProviderException if the email provider call fails.
     */
    public void sendRegistrationVerification(User user) {
        IssuedVerificationToken token = emailVerificationTokenPersistenceService.issueToken(
            user.getId(),
            user.getEmail(),
            EmailVerificationPurpose.REGISTRATION
        );

        emailClient.send(registrationMessage(user.getEmail(), token));
    }

    /**
     * Sends a replacement registration link to an address that still needs verifying.
     *
     * <p>Unknown and already verified addresses are ignored without calling the
     * email provider, so the endpoint cannot be used to discover who has an
     * account.</p>
     *
     * @param rawEmail the address as the client supplied it.
     * @param clientIp the address the request came from.
     * @throws RateLimitExceededException if the email or the client address has asked too many times.
     * @throws EmailProviderException if the email provider call fails.
     */
    public void resendRegistrationVerification(String rawEmail, String clientIp) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);

        rateLimitService.check("verification-resend-email:" + email, rateLimitProperties.verificationResend());
        rateLimitService.check("verification-resend-ip:" + clientIp, rateLimitProperties.verificationResend());

        Optional<User> user = userRepository.findByEmail(email);

        if (user.isEmpty() || user.get().isEmailVerified())
            return;

        sendRegistrationVerification(user.get());
    }

    /**
     * Issues an email-change token for a user and emails its link to the proposed
     * address.
     *
     * <p>The account keeps its current address until the link is confirmed, and
     * the newest request invalidates the previous one.</p>
     *
     * @param user the user asking for the change.
     * @param newEmail the normalised proposed address.
     * @return when the pending change expires.
     * @throws EmailProviderException if the email provider call fails.
     */
    public LocalDateTime sendEmailChangeVerification(User user, String newEmail) {
        rateLimitService.check("email-change-user:" + user.getId(), rateLimitProperties.emailChange());

        IssuedVerificationToken token = emailVerificationTokenPersistenceService.issueToken(
            user.getId(),
            newEmail,
            EmailVerificationPurpose.EMAIL_CHANGE
        );

        emailClient.send(emailChangeMessage(newEmail, token));

        return token.expiresAt();
    }

    /**
     * Confirms a verification link and reports which kind of link it was.
     *
     * <p>The token is consumed first, so a replayed link fails like an unknown
     * one. A registration token marks the account verified and signs it in,
     * returning an access token and a refresh token, because the person holding
     * the link has just proven they own the address and would otherwise be sent
     * to a login form for an account they created a moment ago. An email-change
     * token moves the account to its new address, re-checking that no other
     * account has claimed it in the meantime and leaving the account's verified
     * status alone; it issues nothing, because whoever confirms it usually
     * already has a live session that rotating a refresh token would disturb for
     * no reason.</p>
     *
     * <p>The returned kind is what tells the client which of those happened. It
     * must never be inferred from whether the visitor is already signed in:
     * somebody signed into one account can open a registration link for
     * another.</p>
     *
     * @param rawToken the raw token the frontend read from the link.
     * @return which link was confirmed, plus a session for a registration link.
     * @throws InvalidVerificationTokenException if the token is missing, unknown, expired, invalidated, or used.
     * @throws EmailAlreadyExistsException if another account has claimed the new address.
     */
    @Transactional
    public VerifyEmailResult verifyEmail(String rawToken) {
        EmailVerificationToken token = emailVerificationTokenPersistenceService.consumeToken(rawToken);
        User user = userRepository.findById(token.getUserId())
            .orElseThrow(() -> new InvalidVerificationTokenException());

        if (token.getPurpose() == EmailVerificationPurpose.REGISTRATION)
            return verifyRegistration(user);

        applyEmailChange(user, token.getEmail());

        return new VerifyEmailResult(
            new VerifyEmailResponse(EmailVerificationPurpose.EMAIL_CHANGE, null, user.getEmail(), null),
            null
        );
    }

    private VerifyEmailResult verifyRegistration(User user) {
        if (!user.isEmailVerified()) {
            user.markEmailVerified(LocalDateTime.now(ZoneOffset.UTC));
            userRepository.save(user);
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenPersistenceService.issueRefreshToken(user.getId());

        return new VerifyEmailResult(
            new VerifyEmailResponse(
                EmailVerificationPurpose.REGISTRATION,
                user.getName(),
                user.getEmail(),
                accessToken
            ),
            refreshToken
        );
    }

    private void applyEmailChange(User user, String newEmail) {
        if (newEmail.equals(user.getEmail()))
            return;

        if (userRepository.existsByEmail(newEmail))
            throw new EmailAlreadyExistsException(newEmail);

        user.updateEmail(newEmail);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyExistsException(newEmail);
        }
    }

    private EmailMessage registrationMessage(String email, IssuedVerificationToken token) {
        String link = verificationLink(token.rawToken());
        String expiry = expiry(token.expiresAt());

        String text = """
            Welcome to Synapse.

            Confirm this address to finish creating your Synapse account:

            %s

            This link can only be used once and expires at %s.

            If you did not create a Synapse account, ignore this email.
            """.formatted(link, expiry);

        String html = """
            <p>Welcome to Synapse.</p>
            <p>Confirm this address to finish creating your Synapse account:</p>
            <p><a href="%s">Verify my email address</a></p>
            <p>This link can only be used once and expires at %s.</p>
            <p>If you did not create a Synapse account, ignore this email.</p>
            """.formatted(link, expiry);

        return new EmailMessage(email, "Verify your Synapse email address", text, html, idempotencyKey(token));
    }

    private EmailMessage emailChangeMessage(String email, IssuedVerificationToken token) {
        String link = verificationLink(token.rawToken());
        String expiry = expiry(token.expiresAt());

        String text = """
            A Synapse account asked to change its email address to this one.

            Confirm this address to move the account to it:

            %s

            The account keeps its current address until you confirm. This link can
            only be used once and expires at %s.

            If you did not ask for this change, ignore this email.
            """.formatted(link, expiry);

        String html = """
            <p>A Synapse account asked to change its email address to this one.</p>
            <p>Confirm this address to move the account to it:</p>
            <p><a href="%s">Confirm my new email address</a></p>
            <p>The account keeps its current address until you confirm. This link can
            only be used once and expires at %s.</p>
            <p>If you did not ask for this change, ignore this email.</p>
            """.formatted(link, expiry);

        return new EmailMessage(email, "Confirm your new Synapse email address", text, html, idempotencyKey(token));
    }

    private String verificationLink(String rawToken) {
        return emailVerificationProperties.frontendUrl()
            + "?token="
            + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private String expiry(LocalDateTime expiresAt) {
        return EXPIRY_FORMAT.format(expiresAt) + " UTC";
    }

    private String idempotencyKey(IssuedVerificationToken token) {
        return IDEMPOTENCY_KEY_PREFIX + token.id();
    }

}

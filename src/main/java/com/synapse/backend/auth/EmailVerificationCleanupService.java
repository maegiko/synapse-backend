package com.synapse.backend.auth;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.synapse.backend.user.UserRepository;

import jakarta.transaction.Transactional;

/**
 * Removes the leftovers of registrations that were never confirmed, and the
 * emailed tokens that can no longer be used.
 *
 * <p>Strict registration issues no access token, so an unverified account owns no
 * notes, decks, quizzes, or scores. Deleting it cascades its verification tokens
 * in the database and frees its email address for a later registration.</p>
 *
 * <p>Expired password reset tokens are swept here too, on the same schedule, so
 * the application keeps one cleanup job rather than one per token table.</p>
 */
@Service
public class EmailVerificationCleanupService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenPersistenceService emailVerificationTokenPersistenceService;
    private final PasswordResetTokenPersistenceService passwordResetTokenPersistenceService;
    private final EmailVerificationProperties emailVerificationProperties;
    private final Clock clock;

    public EmailVerificationCleanupService(
        UserRepository userRepository,
        EmailVerificationTokenPersistenceService emailVerificationTokenPersistenceService,
        PasswordResetTokenPersistenceService passwordResetTokenPersistenceService,
        EmailVerificationProperties emailVerificationProperties,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.emailVerificationTokenPersistenceService = emailVerificationTokenPersistenceService;
        this.passwordResetTokenPersistenceService = passwordResetTokenPersistenceService;
        this.emailVerificationProperties = emailVerificationProperties;
        this.clock = clock;
    }

    /**
     * Deletes never-verified accounts older than the configured retention, and
     * every verification token that has already expired.
     *
     * <p>Verified accounts are never touched, however old they are, and an
     * unverified account inside the retention window keeps its chance to confirm.
     * The first run waits one interval, so a restart never sweeps during startup.</p>
     */
    @Transactional
    @Scheduled(
        fixedDelayString = "${auth.email-verification.cleanup-interval}",
        initialDelayString = "${auth.email-verification.cleanup-interval}"
    )
    public void removeAbandonedRegistrations() {
        LocalDateTime now = LocalDateTime.now(clock);

        userRepository.deleteUnverifiedCreatedBefore(now.minus(emailVerificationProperties.unverifiedRetention()));
        emailVerificationTokenPersistenceService.deleteExpiredTokens(now);
    }

    /**
     * Deletes every password reset token that has already expired.
     *
     * <p>Kept separate from the registration sweep because it deletes nothing but
     * tokens and touches no account, and it shares that sweep's interval so the
     * application still runs one cleanup schedule.</p>
     */
    @Transactional
    @Scheduled(
        fixedDelayString = "${auth.email-verification.cleanup-interval}",
        initialDelayString = "${auth.email-verification.cleanup-interval}"
    )
    public void removeExpiredPasswordResetTokens() {
        passwordResetTokenPersistenceService.deleteExpiredTokens(LocalDateTime.now(clock));
    }

}

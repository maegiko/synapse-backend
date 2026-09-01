package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.synapse.backend.support.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
class EmailVerificationCleanupIntegrationTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final String VALID_PASSWORD = "password123";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmailVerificationCleanupService emailVerificationCleanupService;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void resetDatabaseAndClock() {
        jdbcTemplate.execute("DELETE FROM app_user");
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void unverifiedAccountsOlderThanTheRetentionAreDeletedWithTheirTokens() {
        createUnverifiedUser("stale@example.com", NOW.minus(Duration.ofDays(8)));

        emailVerificationCleanupService.removeAbandonedRegistrations();

        assertThat(countUsers("stale@example.com")).isZero();
        assertThat(countTokens()).isZero();
    }

    @Test
    void recentUnverifiedAccountsAndTheirLiveLinksAreKept() {
        createUnverifiedUser("fresh@example.com", NOW.minus(Duration.ofHours(6)));

        emailVerificationCleanupService.removeAbandonedRegistrations();

        assertThat(countUsers("fresh@example.com")).isEqualTo(1);
        assertThat(countTokens()).isEqualTo(1);
    }

    @Test
    void anUnverifiedAccountInsideTheRetentionSurvivesItsExpiredLink() {
        createUnverifiedUser("fresh@example.com", NOW.minus(Duration.ofDays(6)));

        emailVerificationCleanupService.removeAbandonedRegistrations();

        // Still inside the retention window, so the account stays and can ask for a replacement
        // link, while the link that can no longer be used is swept.
        assertThat(countUsers("fresh@example.com")).isEqualTo(1);
        assertThat(countTokens()).isZero();
    }

    @Test
    void verifiedAccountsAreNeverDeletedHoweverOldTheyAre() throws Exception {
        registerVerifiedUser("Kenneth", "kenneth@example.com", VALID_PASSWORD, null);
        jdbcTemplate.update(
            "UPDATE app_user SET created_at = ? WHERE email = ?",
            LocalDateTime.ofInstant(NOW.minus(Duration.ofDays(400)), ZoneOffset.UTC),
            "kenneth@example.com"
        );

        emailVerificationCleanupService.removeAbandonedRegistrations();

        assertThat(countUsers("kenneth@example.com")).isEqualTo(1);
    }

    @Test
    void expiredTokensOfALiveAccountAreDeletedAndUsableOnesAreKept() throws Exception {
        registerVerifiedUser("Kenneth", "kenneth@example.com", VALID_PASSWORD, null);

        Long userId = jdbcTemplate.queryForObject(
            "SELECT id FROM app_user WHERE email = ?",
            Long.class,
            "kenneth@example.com"
        );

        insertToken(userId, "kenneth@example.com", "expired-hash", NOW.minus(Duration.ofHours(1)));
        insertToken(userId, "kenneth@example.com", "live-hash", NOW.plus(Duration.ofHours(1)));

        emailVerificationCleanupService.removeAbandonedRegistrations();

        assertThat(countTokensWithHash("expired-hash")).isZero();
        assertThat(countTokensWithHash("live-hash")).isEqualTo(1);
        assertThat(countUsers("kenneth@example.com")).isEqualTo(1);
    }

    @Test
    void cleanupWithNothingToRemoveChangesNothing() throws Exception {
        registerVerifiedUser("Kenneth", "kenneth@example.com", VALID_PASSWORD, null);
        createUnverifiedUser("fresh@example.com", NOW.minus(Duration.ofHours(2)));

        emailVerificationCleanupService.removeAbandonedRegistrations();

        assertThat(countUsers("kenneth@example.com")).isEqualTo(1);
        assertThat(countUsers("fresh@example.com")).isEqualTo(1);
    }

    /** An abandoned registration: an unverified account created at the given time, with its token. */
    private void createUnverifiedUser(String email, Instant createdAt) {
        LocalDateTime created = LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC);

        Long userId = jdbcTemplate.queryForObject(
            """
            INSERT INTO app_user (full_name, email, password_hash, created_at)
            VALUES ('Kenneth', ?, 'hash', ?)
            RETURNING id
            """,
            Long.class,
            email,
            created
        );

        insertToken(userId, email, email + "-hash", createdAt.plus(Duration.ofDays(1)));
    }

    private void insertToken(Long userId, String email, String tokenHash, Instant expiresAt) {
        jdbcTemplate.update(
            """
            INSERT INTO email_verification_token (user_id, email, purpose, token_hash, expires_at)
            VALUES (?, ?, 'REGISTRATION', ?, ?)
            """,
            userId,
            email,
            tokenHash,
            LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        );
    }

    private int countUsers(String email) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user WHERE email = ?", Integer.class, email);
    }

    private int countTokens() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM email_verification_token", Integer.class);
    }

    private int countTokensWithHash(String tokenHash) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM email_verification_token WHERE token_hash = ?",
            Integer.class,
            tokenHash
        );
    }

}

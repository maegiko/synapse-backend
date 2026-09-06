package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import com.synapse.backend.auth.exceptions.GoogleAccountConflictException;
import com.synapse.backend.auth.exceptions.IncorrectPasswordException;
import com.synapse.backend.support.PostgresIntegrationTest;

/**
 * One account, two Google Accounts, both arriving at once.
 *
 * <p>Read the account, see a null subject, then write one, and two credentials carrying
 * <em>different</em> subjects both pass: the unique index never fires, because the two values
 * differ. The account ends up linked to whichever wrote last and both callers are let in,
 * which is exactly the merge the sequential path refuses with a conflict. The conditional
 * update is what closes that, so this asserts the guarantee rather than the implementation.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class GoogleLinkConcurrencyIntegrationTest extends PostgresIntegrationTest {

    private static final String EMAIL = "ada@gmail.com";
    private static final String PASSWORD_HASH = "$2a$10$notarealbcrypthashbutstoredasone000000000000000000000000";
    private static final String SUBJECT_A = "112233445566778899000";
    private static final String SUBJECT_B = "998877665544332211000";
    private static final String RESET_HASH = "$2a$10$adifferenthashaltogetherafterareset0000000000000000000000";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GoogleAccountPersistenceService googleAccountPersistenceService;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void twoGoogleAccountsRacingToLinkOneAccountLeaveExactlyOneLinked() throws Exception {
        long userId = createVerifiedUser();

        List<Throwable> failures = linkConcurrently(userId, SUBJECT_A, SUBJECT_B);

        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(GoogleAccountConflictException.class);
        assertThat(googleSubjectOf(userId)).isIn(SUBJECT_A, SUBJECT_B);
    }

    /**
     * A password reset between checking the password and writing the subject must not be
     * undone. Somebody holding the old password and a still-valid access token would
     * otherwise attach their own Google Account moments after the owner recovered it, leaving
     * a way in that the reset existed to close.
     */
    @Test
    void linkingIsRefusedWhenThePasswordChangedAfterItWasChecked() {
        long userId = createVerifiedUser();

        jdbcTemplate.update("UPDATE app_user SET password_hash = ? WHERE id = ?", RESET_HASH, userId);

        assertThatThrownBy(() ->
            googleAccountPersistenceService.linkWithPassword(userId, SUBJECT_A, PASSWORD_HASH)
        ).isInstanceOf(IncorrectPasswordException.class);

        assertThat(googleSubjectOf(userId)).isNull();
    }

    @Test
    void linkingSucceedsWhileThePasswordIsStillTheOneThatWasChecked() {
        long userId = createVerifiedUser();

        googleAccountPersistenceService.linkWithPassword(userId, SUBJECT_A, PASSWORD_HASH);

        assertThat(googleSubjectOf(userId)).isEqualTo(SUBJECT_A);
    }

    @Test
    void unlinkingReportsWhetherThereWasAnythingToRemove() {
        long userId = createVerifiedUser();

        googleAccountPersistenceService.link(userId, SUBJECT_A);

        assertThat(googleAccountPersistenceService.unlink(userId)).isTrue();
        assertThat(googleAccountPersistenceService.unlink(userId)).isFalse();
        assertThat(googleSubjectOf(userId)).isNull();
    }

    @Test
    void theSecondLinkIsRefusedRatherThanOverwritingTheFirst() {
        long userId = createVerifiedUser();

        googleAccountPersistenceService.link(userId, SUBJECT_A);

        assertThat(googleSubjectOf(userId)).isEqualTo(SUBJECT_A);

        try {
            googleAccountPersistenceService.link(userId, SUBJECT_B);
        } catch (GoogleAccountConflictException expected) {
            assertThat(googleSubjectOf(userId)).isEqualTo(SUBJECT_A);
            return;
        }

        throw new AssertionError("The second subject overwrote the first");
    }

    /** Runs two links at one barrier and returns whatever they threw. */
    private List<Throwable> linkConcurrently(long userId, String... subjects) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(subjects.length);
        CyclicBarrier barrier = new CyclicBarrier(subjects.length);
        List<Future<Throwable>> futures = new ArrayList<>();

        for (String subject : subjects) {
            futures.add(executor.submit(() -> {
                barrier.await();

                try {
                    googleAccountPersistenceService.link(userId, subject);
                    return null;
                } catch (Throwable thrown) {
                    return thrown;
                }
            }));
        }

        List<Throwable> failures = new ArrayList<>();

        for (Future<Throwable> future : futures) {
            Throwable thrown = future.get();
            if (thrown != null) failures.add(thrown);
        }

        executor.shutdown();

        return failures;
    }

    private long createVerifiedUser() {
        jdbcTemplate.update(
            """
            INSERT INTO app_user (full_name, email, password_hash, email_verified_at)
            VALUES (?, ?, ?, ?)
            """,
            "Ada Lovelace",
            EMAIL,
            PASSWORD_HASH,
            LocalDateTime.now(ZoneOffset.UTC)
        );

        return jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, EMAIL);
    }

    private String googleSubjectOf(long userId) {
        return jdbcTemplate.queryForObject(
            "SELECT google_subject FROM app_user WHERE id = ?",
            String.class,
            userId
        );
    }

}

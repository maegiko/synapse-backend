package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.synapse.backend.support.PostgresIntegrationTest;

/**
 * What the V27 migration guarantees, asserted against the database rather than the service
 * that relies on it. The uniqueness of a Google subject is what makes a concurrent first
 * login safe, and the check constraint is what stops an account being left with no way in.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GoogleAccountSchemaIntegrationTest extends PostgresIntegrationTest {

    private static final String SUBJECT = "112233445566778899000";
    private static final String PASSWORD_HASH = "$2a$10$notarealbcrypthashbutstoredasone000000000000000000000000";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void anAccountMayHaveNoPasswordWhenItHasAGoogleSubject() {
        insertUser("ada@gmail.com", null, SUBJECT);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT password_hash FROM app_user WHERE email = ?",
            String.class,
            "ada@gmail.com"
        )).isNull();
    }

    @Test
    void anAccountMayHaveNoGoogleSubjectWhenItHasAPassword() {
        insertUser("kenneth@example.com", PASSWORD_HASH, null);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT google_subject FROM app_user WHERE email = ?",
            String.class,
            "kenneth@example.com"
        )).isNull();
    }

    @Test
    void oneGoogleSubjectCannotReachTwoAccounts() {
        insertUser("ada@gmail.com", null, SUBJECT);

        assertThatThrownBy(() -> insertUser("ada.other@gmail.com", null, SUBJECT))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void severalAccountsMayHaveNoGoogleSubjectAtAll() {
        insertUser("kenneth@example.com", PASSWORD_HASH, null);
        insertUser("ada@example.com", PASSWORD_HASH, null);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isEqualTo(2);
    }

    @Test
    void anAccountCannotBeCreatedWithNoWayIn() {
        assertThatThrownBy(() -> insertUser("nobody@example.com", null, null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAccountCannotBeLeftWithNoWayIn() {
        insertUser("ada@gmail.com", null, SUBJECT);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE app_user SET google_subject = NULL WHERE email = ?",
            "ada@gmail.com"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertUser(String email, String passwordHash, String googleSubject) {
        jdbcTemplate.update(
            """
            INSERT INTO app_user (full_name, email, password_hash, google_subject, email_verified_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            "Ada Lovelace",
            email,
            passwordHash,
            googleSubject,
            LocalDateTime.now(ZoneOffset.UTC)
        );
    }

}

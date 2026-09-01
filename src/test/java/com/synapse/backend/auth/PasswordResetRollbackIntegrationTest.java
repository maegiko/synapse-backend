package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.synapse.backend.support.PostgresIntegrationTest;

/**
 * A reset is one transaction: consuming the link, writing the password, and
 * revoking the user's sessions commit together. A failure partway through must
 * leave the link usable rather than burning it on a reset that never happened.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetRollbackIntegrationTest extends PostgresIntegrationTest {

    private static final String RESET_ENDPOINT = "/api/auth/password/reset";
    private static final String EMAIL = "kenneth@example.com";
    private static final String RESET_TOKEN = "rollback-password-reset-token";
    private static final String NEW_PASSWORD = "new-password123";
    private static final String EXISTING_HASH = "$2a$10$notarealbcrypthashbutstoredasone000000000000000000000000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private RefreshTokenPersistenceService refreshTokenPersistenceService;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void failureWhileRevokingSessionsLeavesTheResetLinkUsableAndThePasswordAlone() throws Exception {
        createUserWithResetToken();

        doThrow(new IllegalStateException("revocation failed"))
            .when(refreshTokenPersistenceService).revokeAllRefreshTokens(anyLong());

        assertThatThrownBy(() -> mockMvc.perform(post(RESET_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + RESET_TOKEN + "\", \"newPassword\": \"" + NEW_PASSWORD + "\"}")))
            .hasRootCauseInstanceOf(IllegalStateException.class);

        Map<String, Object> tokenRow = jdbcTemplate.queryForMap(
            "SELECT * FROM password_reset_token WHERE token_hash = ?",
            sha256Hex(RESET_TOKEN)
        );

        assertThat(tokenRow.get("consumed_at")).isNull();
        assertThat(passwordHashOf(EMAIL)).isEqualTo(EXISTING_HASH);
    }

    private void createUserWithResetToken() {
        jdbcTemplate.update(
            "INSERT INTO app_user (full_name, email, password_hash, email_verified_at) VALUES (?, ?, ?, ?)",
            "Kenneth",
            EMAIL,
            EXISTING_HASH,
            LocalDateTime.now(ZoneOffset.UTC)
        );

        Long userId = jdbcTemplate.queryForObject(
            "SELECT id FROM app_user WHERE email = ?",
            Long.class,
            EMAIL
        );

        jdbcTemplate.update(
            "INSERT INTO password_reset_token (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
            userId,
            sha256Hex(RESET_TOKEN),
            LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30)
        );
    }

    private String passwordHashOf(String email) {
        return jdbcTemplate.queryForObject(
            "SELECT password_hash FROM app_user WHERE email = ?",
            String.class,
            email
        );
    }

    private String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

}

package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.email.dto.EmailMessage;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

/**
 * How a reset token is stored, replaced, and consumed, and the wall between the
 * two kinds of emailed token: a verification link must never set a password, and
 * a reset link must never confirm an address.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetTokenIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String FORGOT_ENDPOINT = "/api/auth/password/forgot";
    private static final String RESET_ENDPOINT = "/api/auth/password/reset";
    private static final String VERIFY_ENDPOINT = "/api/auth/email/verify";
    private static final String EMAIL = "kenneth@example.com";
    private static final String VALID_PASSWORD = "password123";
    private static final String NEW_PASSWORD = "new-password123";
    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired password reset token.";

    private static final Pattern RESET_LINK_PATTERN = Pattern.compile("reset-password\\?token=([A-Za-z0-9\\-_%]+)");
    private static final Pattern VERIFY_LINK_PATTERN = Pattern.compile("verify-email\\?token=([A-Za-z0-9\\-_%]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void onlyTheHashOfTheTokenIsStored() throws Exception {
        String token = requestResetAndReadTheLink();

        Map<String, Object> row = tokenRow();

        assertThat(row.get("token_hash")).isEqualTo(sha256Hex(token));
        assertThat(row.get("token_hash")).isNotEqualTo(token);
        assertThat(row.get("token_hash").toString()).hasSize(64);
        assertThat(row.get("token_hash").toString()).matches("[0-9a-f]{64}");
        assertThat(countTokensMatching(token)).isZero();
    }

    @Test
    void aResetTokenExpiresInAboutHalfAnHour() throws Exception {
        requestResetAndReadTheLink();

        LocalDateTime expiresAt = ((Timestamp) tokenRow().get("expires_at")).toLocalDateTime();

        assertThat(expiresAt)
            .isAfter(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(25))
            .isBefore(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(35));
    }

    @Test
    void aNewRequestInvalidatesThePreviousTokenAndLeavesExactlyOneActive() throws Exception {
        String firstToken = requestResetAndReadTheLink();
        String replacementToken = requestResetAndReadTheLink();

        assertThat(replacementToken).isNotEqualTo(firstToken);
        assertThat(invalidatedAt(firstToken)).isNotNull();
        assertThat(countTokens()).isEqualTo(2);
        assertThat(countActiveTokens()).isEqualTo(1);

        resetPassword(firstToken, NEW_PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));

        resetPassword(replacementToken, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void resetReturnsTheGenericErrorForAnUnknownToken() throws Exception {
        resetPassword("a-token-that-was-never-issued", NEW_PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    void resetReturnsTheGenericErrorForAnExpiredToken() throws Exception {
        String token = requestResetAndReadTheLink();

        jdbcTemplate.update("UPDATE password_reset_token SET expires_at = ?",
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        resetPassword(token, NEW_PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));

        login(EMAIL, VALID_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void resetReturnsTheGenericErrorWhenTheSameTokenIsUsedTwice() throws Exception {
        String token = requestResetAndReadTheLink();

        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());

        resetPassword(token, "another-password123")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));

        login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void onlyOneOfTwoConcurrentResetsWithTheSameTokenSucceeds() throws Exception {
        String token = requestResetAndReadTheLink();

        List<Integer> statuses = resetConcurrently(token, 2);

        assertThat(statuses).containsExactlyInAnyOrder(204, 400);
        assertThat(countConsumedTokens()).isEqualTo(1);
        login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void aVerificationTokenCannotResetAPassword() throws Exception {
        register("ada@example.com").andExpect(status().isAccepted());
        String verificationToken = rawTokenFromLastEmail(VERIFY_LINK_PATTERN);

        resetPassword(verificationToken, NEW_PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));

        // The verification token is untouched by the attempt and still confirms the address.
        assertThat(countTokens()).isZero();
        verifyEmail(verificationToken).andExpect(status().isOk());
    }

    @Test
    void aResetTokenCannotConfirmAnEmailAddress() throws Exception {
        String resetToken = requestResetAndReadTheLink();

        verifyEmail(resetToken)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid or expired verification token."));

        // Still usable for what it was issued for.
        assertThat(consumedAt(resetToken)).isNull();
        resetPassword(resetToken, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void deletingAUserDeletesTheirResetTokens() throws Exception {
        requestResetAndReadTheLink();

        jdbcTemplate.update("DELETE FROM app_user WHERE email = ?", EMAIL);

        assertThat(countTokens()).isZero();
    }

    private ResultActions register(String email) throws Exception {
        return mockMvc.perform(post(REGISTER_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new RegisterRequest("Kenneth", email, VALID_PASSWORD))));
    }

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post(FORGOT_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions resetPassword(String token, String newPassword) throws Exception {
        return mockMvc.perform(post(RESET_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("token", token, "newPassword", newPassword))));
    }

    private ResultActions verifyEmail(String token) throws Exception {
        return mockMvc.perform(post(VERIFY_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("token", token))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    /** Registers a verified user if needed, asks for a reset, and reads the emailed link. */
    private String requestResetAndReadTheLink() throws Exception {
        if (countUsers(EMAIL) == 0)
            registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);

        reset(emailClient());

        forgot(EMAIL).andExpect(status().isNoContent());

        return rawTokenFromLastEmail(RESET_LINK_PATTERN);
    }

    private List<Integer> resetConcurrently(String token, int attempts) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CyclicBarrier barrier = new CyclicBarrier(attempts);
        List<Future<Integer>> results = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            results.add(executor.submit(() -> {
                barrier.await();

                return resetPassword(token, NEW_PASSWORD).andReturn().getResponse().getStatus();
            }));
        }

        List<Integer> statuses = new ArrayList<>();

        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }

        executor.shutdown();

        return statuses;
    }

    /** Reads the raw token back out of the most recent emailed link, the way the frontend page does. */
    private String rawTokenFromLastEmail(Pattern linkPattern) {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);

        verify(emailClient(), atLeastOnce()).send(captor.capture());

        Matcher matcher = linkPattern.matcher(captor.getValue().text());

        assertThat(matcher.find()).isTrue();

        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    private Map<String, Object> tokenRow() {
        return jdbcTemplate.queryForMap("SELECT * FROM password_reset_token");
    }

    private Object invalidatedAt(String token) {
        return jdbcTemplate.queryForMap(
            "SELECT invalidated_at FROM password_reset_token WHERE token_hash = ?",
            sha256Hex(token)
        ).get("invalidated_at");
    }

    private Object consumedAt(String token) {
        return jdbcTemplate.queryForMap(
            "SELECT consumed_at FROM password_reset_token WHERE token_hash = ?",
            sha256Hex(token)
        ).get("consumed_at");
    }

    private int countTokens() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM password_reset_token", Integer.class);
    }

    private int countActiveTokens() {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM password_reset_token
            WHERE consumed_at IS NULL AND invalidated_at IS NULL AND expires_at > CURRENT_TIMESTAMP
            """,
            Integer.class
        );
    }

    private int countConsumedTokens() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM password_reset_token WHERE consumed_at IS NOT NULL",
            Integer.class
        );
    }

    private int countTokensMatching(String token) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM password_reset_token WHERE token_hash = ?",
            Integer.class,
            token
        );
    }

    private int countUsers(String email) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user WHERE email = ?", Integer.class, email);
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

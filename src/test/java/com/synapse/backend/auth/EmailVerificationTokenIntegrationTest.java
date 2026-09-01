package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.email.dto.EmailMessage;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class EmailVerificationTokenIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String RESEND_ENDPOINT = "/api/auth/email/resend";
    private static final String VERIFY_ENDPOINT = "/api/auth/email/verify";
    private static final String EMAIL = "kenneth@example.com";
    private static final String VALID_PASSWORD = "password123";
    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired verification token.";

    private static final Pattern LINK_PATTERN = Pattern.compile("verify-email\\?token=([A-Za-z0-9\\-_%]+)");

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
    void verifyReturnsBadRequestWhenTheTokenIsMissing() throws Exception {
        mockMvc.perform(post(VERIFY_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("token: must not be blank"));
    }

    @Test
    void verifyReturnsTheGenericErrorForAnUnknownToken() throws Exception {
        verifyEmail("a-token-that-was-never-issued")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    void verifyReturnsTheGenericErrorForAnExpiredToken() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());
        String token = rawTokenFromLastEmail();

        jdbcTemplate.update("UPDATE email_verification_token SET expires_at = ?",
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        verifyEmail(token)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));

        assertThat(emailVerifiedAt(EMAIL)).isNull();
    }

    @Test
    void verifyReturnsTheGenericErrorForAnInvalidatedToken() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());
        String firstToken = rawTokenFromLastEmail();

        resend(EMAIL).andExpect(status().isNoContent());
        String replacementToken = rawTokenFromLastEmail();

        assertThat(replacementToken).isNotEqualTo(firstToken);
        assertThat(invalidatedAt(firstToken)).isNotNull();

        verifyEmail(firstToken)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));

        verifyEmail(replacementToken).andExpect(status().isOk());
    }

    @Test
    void verifyReturnsTheGenericErrorWhenTheSameTokenIsUsedTwice() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());
        String token = rawTokenFromLastEmail();

        verifyEmail(token).andExpect(status().isOk());

        verifyEmail(token)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    void onlyOneOfTwoConcurrentConfirmationsOfTheSameTokenSucceeds() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());
        String token = rawTokenFromLastEmail();

        List<Integer> statuses = verifyConcurrently(token, 2);

        assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        assertThat(emailVerifiedAt(EMAIL)).isNotNull();
        assertThat(countConsumedTokens()).isEqualTo(1);
    }

    @Test
    void issuingAReplacementInvalidatesThePreviousTokenAndLeavesExactlyOneActive() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());

        resend(EMAIL).andExpect(status().isNoContent());

        assertThat(countTokens()).isEqualTo(2);
        assertThat(countActiveTokens()).isEqualTo(1);
    }

    @Test
    void onlyTheHashOfTheTokenIsStored() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());
        String token = rawTokenFromLastEmail();

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM email_verification_token");

        assertThat(row.get("token_hash")).isEqualTo(sha256Hex(token));
        assertThat(row.get("token_hash")).isNotEqualTo(token);
        assertThat(row.get("token_hash").toString()).hasSize(64);
        assertThat(countTokensMatching(token)).isZero();
    }

    @Test
    void tokenHashesAreUnique() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM email_verification_token");

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO email_verification_token (user_id, email, purpose, token_hash, expires_at)
            VALUES (?, ?, 'REGISTRATION', ?, CURRENT_TIMESTAMP + INTERVAL '1 day')
            """,
            row.get("user_id"),
            EMAIL,
            row.get("token_hash")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAUserDeletesTheirVerificationTokens() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());

        jdbcTemplate.update("DELETE FROM app_user WHERE email = ?", EMAIL);

        assertThat(countTokens()).isZero();
    }

    private ResultActions register(String email) throws Exception {
        return mockMvc.perform(post(REGISTER_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new RegisterRequest("Kenneth", email, VALID_PASSWORD))));
    }

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(post(RESEND_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\": \"" + email + "\"}"));
    }

    private ResultActions verifyEmail(String token) throws Exception {
        return mockMvc.perform(post(VERIFY_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("token", token))));
    }

    private List<Integer> verifyConcurrently(String token, int attempts) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CyclicBarrier barrier = new CyclicBarrier(attempts);
        List<Future<Integer>> results = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            results.add(executor.submit(() -> {
                barrier.await();

                return verifyEmail(token).andReturn().getResponse().getStatus();
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
    private String rawTokenFromLastEmail() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);

        verify(emailClient(), atLeastOnce()).send(captor.capture());

        Matcher matcher = LINK_PATTERN.matcher(captor.getValue().text());

        assertThat(matcher.find()).isTrue();

        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    private Object emailVerifiedAt(String email) {
        return jdbcTemplate.queryForMap("SELECT email_verified_at FROM app_user WHERE email = ?", email)
            .get("email_verified_at");
    }

    private Object invalidatedAt(String token) {
        return jdbcTemplate.queryForMap(
            "SELECT invalidated_at FROM email_verification_token WHERE token_hash = ?",
            sha256Hex(token)
        ).get("invalidated_at");
    }

    private int countTokens() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM email_verification_token", Integer.class);
    }

    private int countActiveTokens() {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM email_verification_token
            WHERE consumed_at IS NULL AND invalidated_at IS NULL AND expires_at > CURRENT_TIMESTAMP
            """,
            Integer.class
        );
    }

    private int countConsumedTokens() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM email_verification_token WHERE consumed_at IS NOT NULL",
            Integer.class
        );
    }

    private int countTokensMatching(String token) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM email_verification_token WHERE token_hash = ?",
            Integer.class,
            token
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

package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MvcResult;

import com.synapse.backend.auth.dto.GoogleClaims;
import com.synapse.backend.auth.dto.GoogleLoginRequest;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * Claiming an unverified account is several writes — the subject, the verified stamp, the
 * cleared password, the replaced profile, the invalidated registration links — followed by
 * issuing a session. They share one transaction, so a failure while issuing the session must
 * leave the account exactly as it was rather than half claimed and passwordless.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GoogleAuthRollbackIntegrationTest extends PostgresIntegrationTest {

    private static final String NONCE_ENDPOINT = "/api/auth/google/nonce";
    private static final String GOOGLE_ENDPOINT = "/api/auth/google";
    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String NONCE_COOKIE = "googleNonce";
    private static final String CREDENTIAL = "google-id-token";
    private static final String SUBJECT = "112233445566778899000";
    private static final String GMAIL = "ada@gmail.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private GoogleTokenVerifier googleTokenVerifier;

    @MockitoBean
    private RefreshTokenPersistenceService refreshTokenPersistenceService;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void aFailureWhileIssuingTheSessionLeavesTheUnverifiedAccountUntouched() throws Exception {
        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new RegisterRequest("Impostor", GMAIL, "attacker-password"))))
            .andExpect(status().isAccepted());

        Map<String, Object> before = userRow();

        doThrow(new IllegalStateException("refresh token issue failed"))
            .when(refreshTokenPersistenceService).issueRefreshToken(anyLong());

        String nonce = issuedNonce();

        doReturn(new GoogleClaims(SUBJECT, GMAIL, true, "Ada Lovelace", null))
            .when(googleTokenVerifier).verify(any(), any());

        assertThatThrownBy(() -> mockMvc.perform(post(GOOGLE_ENDPOINT)
                .cookie(new Cookie(NONCE_COOKIE, nonce))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GoogleLoginRequest(CREDENTIAL)))))
            .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(userRow()).isEqualTo(before);
        assertThat(activeRegistrationTokens()).isEqualTo(1);
    }

    private String issuedNonce() throws Exception {
        MvcResult result = mockMvc.perform(post(NONCE_ENDPOINT))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("nonce").asString();
    }

    private Map<String, Object> userRow() {
        return jdbcTemplate.queryForMap(
            "SELECT full_name, email, password_hash, google_subject, time_zone, email_verified_at "
                + "FROM app_user WHERE email = ?",
            GMAIL
        );
    }

    private int activeRegistrationTokens() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM email_verification_token "
                + "WHERE email = ? AND consumed_at IS NULL AND invalidated_at IS NULL",
            Integer.class,
            GMAIL
        );
    }

}

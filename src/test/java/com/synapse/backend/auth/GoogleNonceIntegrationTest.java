package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.auth.dto.GoogleClaims;
import com.synapse.backend.auth.dto.GoogleLoginRequest;
import com.synapse.backend.auth.exceptions.InvalidGoogleCredentialException;
import com.synapse.backend.support.PostgresIntegrationTest;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * The nonce is what ties a Google credential to the browser that asked for it and to one
 * attempt. Missing, unknown, mismatched, and replayed nonces all fail the same generic way,
 * whatever the credential itself looks like.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GoogleNonceIntegrationTest extends PostgresIntegrationTest {

    private static final String NONCE_ENDPOINT = "/api/auth/google/nonce";
    private static final String GOOGLE_ENDPOINT = "/api/auth/google";
    private static final String NONCE_COOKIE = "googleNonce";

    private static final String CREDENTIAL = "google-id-token";
    private static final String SUBJECT = "112233445566778899000";
    private static final String GMAIL = "ada@gmail.com";
    private static final String GENERIC_FAILURE = "Google sign-in could not be verified. Try again.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private GoogleTokenVerifier googleTokenVerifier;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void everyNonceIsDifferent() throws Exception {
        assertThat(issuedNonce()).isNotEqualTo(issuedNonce());
    }

    @Test
    void signInWithoutTheNonceCookieIsRejected() throws Exception {
        issuedNonce();

        mockMvc.perform(post(GOOGLE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GoogleLoginRequest(CREDENTIAL))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(GENERIC_FAILURE));

        assertThat(countUsers()).isZero();
    }

    @Test
    void signInWithANonceThatWasNeverIssuedIsRejected() throws Exception {
        signIn("a-nonce-nobody-issued", null)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(GENERIC_FAILURE));

        assertThat(countUsers()).isZero();
    }

    @Test
    void aNonceIsAcceptedOnceAndReplayingItIsRejected() throws Exception {
        String nonce = issuedNonce();

        stubGoogle(nonce);

        signIn(nonce, null).andExpect(status().isOk());

        signIn(nonce, null)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(GENERIC_FAILURE));

        assertThat(countUsers()).isEqualTo(1);
    }

    @Test
    void presentingANonceTheTokenWasNotMintedForIsRejected() throws Exception {
        String tokenNonce = issuedNonce();
        String otherNonce = issuedNonce();

        stubGoogle(tokenNonce);

        signIn(otherNonce, null)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(GENERIC_FAILURE));

        assertThat(countUsers()).isZero();
    }

    @Test
    void theVerifierIsGivenTheNonceFromTheCookie() throws Exception {
        String nonce = issuedNonce();

        stubGoogle(nonce);

        signIn(nonce, null).andExpect(status().isOk());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        verify(googleTokenVerifier).verify(eq(CREDENTIAL), captor.capture());

        assertThat(captor.getValue()).isEqualTo(nonce);
    }

    @Test
    void theNonceCookieIsClearedAfterASuccessfulSignIn() throws Exception {
        String nonce = issuedNonce();

        stubGoogle(nonce);

        MvcResult result = signIn(nonce, null).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie(NONCE_COOKIE);

        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isZero();
    }

    private ResultActions signIn(String nonce, String timeZone) throws Exception {
        return mockMvc.perform(post(GOOGLE_ENDPOINT)
            .cookie(new Cookie(NONCE_COOKIE, nonce))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new GoogleLoginRequest(CREDENTIAL, timeZone))));
    }

    private void stubGoogle(String tokenNonce) {
        doThrow(new InvalidGoogleCredentialException()).when(googleTokenVerifier).verify(any(), any());
        doReturn(new GoogleClaims(SUBJECT, GMAIL, true, "Ada Lovelace", null))
            .when(googleTokenVerifier).verify(eq(CREDENTIAL), eq(tokenNonce));
    }

    private String issuedNonce() throws Exception {
        MvcResult result = mockMvc.perform(post(NONCE_ENDPOINT))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("nonce").asString();
    }

    private int countUsers() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class);
    }

}

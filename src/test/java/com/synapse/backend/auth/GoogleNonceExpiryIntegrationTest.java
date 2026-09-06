package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.synapse.backend.auth.dto.GoogleLoginRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * A nonce that was issued long enough ago is worth no more than one that was never issued.
 * The lifetime is pinned to a millisecond here, so the next request is already past it
 * without the test having to wait.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.google.nonce-ttl=1ms")
class GoogleNonceExpiryIntegrationTest extends PostgresIntegrationTest {

    private static final String NONCE_ENDPOINT = "/api/auth/google/nonce";
    private static final String GOOGLE_ENDPOINT = "/api/auth/google";
    private static final String NONCE_COOKIE = "googleNonce";
    private static final String CREDENTIAL = "google-id-token";

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
    void anExpiredNonceIsRejectedAndTheCredentialIsNeverEvenLookedAt() throws Exception {
        MvcResult nonceResult = mockMvc.perform(post(NONCE_ENDPOINT))
            .andExpect(status().isOk())
            .andReturn();

        String nonce = objectMapper
            .readTree(nonceResult.getResponse().getContentAsString())
            .get("nonce")
            .asString();

        mockMvc.perform(post(GOOGLE_ENDPOINT)
                .cookie(new Cookie(NONCE_COOKIE, nonce))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GoogleLoginRequest(CREDENTIAL))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Google sign-in could not be verified. Try again."));

        verifyNoInteractions(googleTokenVerifier);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isZero();
    }

}

package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.email.dto.EmailMessage;
import com.synapse.backend.support.PostgresIntegrationTest;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * What consuming a reset link does to the account: the password it sets, the
 * sessions it ends, and the session it deliberately does not start.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest extends PostgresIntegrationTest {

    private static final String FORGOT_ENDPOINT = "/api/auth/password/forgot";
    private static final String RESET_ENDPOINT = "/api/auth/password/reset";
    private static final String LOGIN_ENDPOINT = "/api/auth/login";
    private static final String REFRESH_ENDPOINT = "/api/auth/refresh";
    private static final String USER_DETAILS_ENDPOINT = "/api/user/details";
    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String EMAIL = "kenneth@example.com";
    private static final String OTHER_EMAIL = "ada@example.com";
    private static final String VALID_PASSWORD = "password123";
    private static final String NEW_PASSWORD = "new-password123";

    private static final Pattern LINK_PATTERN = Pattern.compile("reset-password\\?token=([A-Za-z0-9\\-_%]+)");

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
    void aValidTokenReplacesTheStoredPasswordHash() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);
        String oldHash = passwordHashOf(EMAIL);

        resetPassword(requestResetLink(), NEW_PASSWORD).andExpect(status().isNoContent());

        String newHash = passwordHashOf(EMAIL);

        assertThat(newHash).isNotEqualTo(oldHash);
        assertThat(newHash).startsWith("$2");
        assertThat(newHash).isNotEqualTo(NEW_PASSWORD);
        assertThat(consumedAt()).isNotNull();
    }

    @Test
    void theOldPasswordStopsWorkingAndTheNewOneStarts() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);

        resetPassword(requestResetLink(), NEW_PASSWORD).andExpect(status().isNoContent());

        login(EMAIL, VALID_PASSWORD)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password."));

        login(EMAIL, NEW_PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void aSuccessfulResetIssuesNoSessionAndClearsTheRefreshCookie() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);

        MvcResult result = resetPassword(requestResetLink(), NEW_PASSWORD)
            .andExpect(status().isNoContent())
            .andReturn();

        Cookie refreshCookie = result.getResponse().getCookie(REFRESH_COOKIE_NAME);

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isEmpty();
        assertThat(refreshCookie.getMaxAge()).isZero();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getPath()).isEqualTo("/api/auth");
    }

    @Test
    void everySessionOfThatUserIsRevoked() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);

        Cookie firstSession = loginAndReadRefreshCookie(EMAIL, VALID_PASSWORD);
        Cookie secondSession = loginAndReadRefreshCookie(EMAIL, VALID_PASSWORD);

        assertThat(countActiveRefreshTokens(EMAIL)).isEqualTo(2);

        resetPassword(requestResetLink(), NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(countActiveRefreshTokens(EMAIL)).isZero();

        // A revoked refresh token cannot be exchanged, so neither browser can carry on.
        refresh(firstSession).andExpect(status().isUnauthorized());
        refresh(secondSession).andExpect(status().isUnauthorized());
    }

    @Test
    void anotherUsersSessionsAndPasswordAreUntouched() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);
        String otherAccessToken = registerAndAuthenticate("Ada", OTHER_EMAIL, VALID_PASSWORD);
        Cookie otherSession = loginAndReadRefreshCookie(OTHER_EMAIL, VALID_PASSWORD);

        resetPassword(requestResetLink(), NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(countActiveRefreshTokens(OTHER_EMAIL)).isEqualTo(2);

        refresh(otherSession).andExpect(status().isOk());
        mockMvc.perform(get(USER_DETAILS_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherAccessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(OTHER_EMAIL));
        login(OTHER_EMAIL, VALID_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void anInvalidTokenChangesNeitherThePasswordNorTheSessions() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);

        Cookie session = loginAndReadRefreshCookie(EMAIL, VALID_PASSWORD);
        String oldHash = passwordHashOf(EMAIL);

        resetPassword("a-token-that-was-never-issued", NEW_PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid or expired password reset token."));

        assertThat(passwordHashOf(EMAIL)).isEqualTo(oldHash);
        assertThat(countActiveRefreshTokens(EMAIL)).isEqualTo(1);

        refresh(session).andExpect(status().isOk());
        login(EMAIL, VALID_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void theNewPasswordMustMeetTheSameRulesAsRegistration() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);
        String token = requestResetLink();
        String oldHash = passwordHashOf(EMAIL);

        resetPassword(token, "short")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("newPassword: size must be between 8 and 64"));

        resetPassword(token, "x".repeat(65))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("newPassword: size must be between 8 and 64"));

        // Inside the character bound but past what BCrypt will hash: 30 three byte characters
        // is 90 bytes. Without the byte bound this reached the encoder and returned a 500.
        String multiByte = "\u5bc6".repeat(30);
        assertThat(multiByte.length()).isLessThanOrEqualTo(64);
        assertThat(multiByte.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(72);

        resetPassword(token, multiByte)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("newPassword: must be at most 72 bytes long"));

        // Validation runs before the token is consumed, so the link survives a typo.
        assertThat(passwordHashOf(EMAIL)).isEqualTo(oldHash);
        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void resetReturnsBadRequestWhenTheTokenIsMissing() throws Exception {
        mockMvc.perform(post(RESET_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\": \"" + NEW_PASSWORD + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("token: must not be blank"));
    }

    @Test
    void resettingRequiresNoAuthentication() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);

        // No bearer token and no cookie: whoever holds the emailed link has locked
        // themselves out and has nothing else to present.
        resetPassword(requestResetLink(), NEW_PASSWORD).andExpect(status().isNoContent());

        login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
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

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post(LOGIN_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private ResultActions refresh(Cookie refreshCookie) throws Exception {
        return mockMvc.perform(post(REFRESH_ENDPOINT).cookie(refreshCookie));
    }

    private Cookie loginAndReadRefreshCookie(String email, String password) throws Exception {
        return login(email, password)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie(REFRESH_COOKIE_NAME);
    }

    /** Asks for a reset for the main account and reads the raw token out of the emailed link. */
    private String requestResetLink() throws Exception {
        reset(emailClient());

        forgot(EMAIL).andExpect(status().isNoContent());

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);

        verify(emailClient(), atLeastOnce()).send(captor.capture());

        Matcher matcher = LINK_PATTERN.matcher(captor.getValue().text());

        assertThat(matcher.find()).isTrue();

        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    private String passwordHashOf(String email) {
        return jdbcTemplate.queryForObject(
            "SELECT password_hash FROM app_user WHERE email = ?",
            String.class,
            email
        );
    }

    private Object consumedAt() {
        return jdbcTemplate.queryForMap("SELECT consumed_at FROM password_reset_token").get("consumed_at");
    }

    private int countActiveRefreshTokens(String email) {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM refresh_token t
            JOIN app_user u ON u.id = t.user_id
            WHERE u.email = ? AND t.revoked_at IS NULL
            """,
            Integer.class,
            email
        );
    }

}

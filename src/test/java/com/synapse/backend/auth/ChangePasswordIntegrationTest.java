package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.auth.dto.ChangePasswordRequest;
import com.synapse.backend.auth.dto.LoginRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ChangePasswordIntegrationTest extends PostgresIntegrationTest {

    private static final String LOGIN_ENDPOINT = "/api/auth/login";
    private static final String REFRESH_ENDPOINT = "/api/auth/refresh";
    private static final String PASSWORD_ENDPOINT = "/api/auth/password";
    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String CURRENT_PASSWORD = "password123";
    private static final String NEW_PASSWORD = "new-password123";
    private static final String EMAIL = "kenneth@example.com";

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
    void changePasswordReturnsNoContentAndStoresANewHash() throws Exception {
        MvcResult registration = registerAndLogin();
        String accessToken = accessToken(registration);
        String oldPasswordHash = passwordHash();

        changePassword(accessToken, new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD))
            .andExpect(status().isNoContent());

        String newPasswordHash = passwordHash();

        assertThat(newPasswordHash).isNotEqualTo(oldPasswordHash);
        assertThat(newPasswordHash).isNotEqualTo(NEW_PASSWORD);
        assertThat(newPasswordHash).startsWith("$2");
    }

    @Test
    void changePasswordClearsTheRefreshTokenCookie() throws Exception {
        String accessToken = accessToken(registerAndLogin());

        MvcResult result = changePassword(accessToken, new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD))
            .andExpect(status().isNoContent())
            .andReturn();

        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE_NAME);
        String setCookieHeader = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(setCookieHeader).contains("SameSite=None");
    }

    @Test
    void changePasswordBlocksLoginWithTheOldPassword() throws Exception {
        String accessToken = accessToken(registerAndLogin());

        changePassword(accessToken, new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD))
            .andExpect(status().isNoContent());

        login(CURRENT_PASSWORD)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void changePasswordAllowsLoginWithTheNewPassword() throws Exception {
        String accessToken = accessToken(registerAndLogin());

        changePassword(accessToken, new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD))
            .andExpect(status().isNoContent());

        login(NEW_PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void changePasswordRevokesEveryRefreshTokenOfTheUser() throws Exception {
        MvcResult registration = registerAndLogin();
        String accessToken = accessToken(registration);
        String registrationToken = refreshCookieValue(registration);
        String secondSessionToken = refreshCookieValue(login(CURRENT_PASSWORD).andExpect(status().isOk()).andReturn());

        assertThat(countActiveTokens()).isEqualTo(2);

        changePassword(accessToken, new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD))
            .andExpect(status().isNoContent());

        assertThat(countActiveTokens()).isZero();
        assertThat(revokedAt(registrationToken)).isNotNull();
        assertThat(revokedAt(secondSessionToken)).isNotNull();
    }

    @Test
    void refreshWithATokenIssuedBeforeThePasswordChangeIsRejected() throws Exception {
        MvcResult registration = registerAndLogin();
        String accessToken = accessToken(registration);
        String refreshToken = refreshCookieValue(registration);

        changePassword(accessToken, new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD))
            .andExpect(status().isNoContent());

        mockMvc.perform(post(REFRESH_ENDPOINT).cookie(new Cookie(REFRESH_COOKIE_NAME, refreshToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid or expired refresh token."));
    }

    @Test
    void changePasswordReturnsUnauthorizedWhenCurrentPasswordIsIncorrect() throws Exception {
        MvcResult registration = registerAndLogin();
        String accessToken = accessToken(registration);
        String refreshToken = refreshCookieValue(registration);
        String oldPasswordHash = passwordHash();

        changePassword(accessToken, new ChangePasswordRequest("wrong-password", NEW_PASSWORD))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Current password is incorrect."));

        assertThat(passwordHash()).isEqualTo(oldPasswordHash);
        assertThat(revokedAt(refreshToken)).isNull();

        login(CURRENT_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void changePasswordReturnsBadRequestWhenNewPasswordIsTooShort() throws Exception {
        String accessToken = accessToken(registerAndLogin());
        String oldPasswordHash = passwordHash();

        changePassword(accessToken, new ChangePasswordRequest(CURRENT_PASSWORD, "short"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("newPassword: size must be between 8 and 64"));

        assertThat(passwordHash()).isEqualTo(oldPasswordHash);
    }

    @Test
    void changePasswordReturnsBadRequestWhenNewPasswordIsMissing() throws Exception {
        String accessToken = accessToken(registerAndLogin());

        changePassword(accessToken, new ChangePasswordRequest(CURRENT_PASSWORD, null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("newPassword: must not be blank"));
    }

    @Test
    void changePasswordReturnsBadRequestWhenCurrentPasswordIsMissing() throws Exception {
        String accessToken = accessToken(registerAndLogin());

        changePassword(accessToken, new ChangePasswordRequest(null, NEW_PASSWORD))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("currentPassword: must not be blank"));
    }

    @Test
    void changePasswordReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        registerAndLogin();
        String oldPasswordHash = passwordHash();

        mockMvc.perform(put(PASSWORD_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD))))
            .andExpect(status().isUnauthorized());

        assertThat(passwordHash()).isEqualTo(oldPasswordHash);
    }

    private ResultActions changePassword(String accessToken, ChangePasswordRequest request) throws Exception {
        return mockMvc.perform(put(PASSWORD_ENDPOINT)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
    }

    /** Registers a user, verifies their address, and logs them in, which is what issues their tokens. */
    private MvcResult registerAndLogin() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, CURRENT_PASSWORD, null);

        return login(CURRENT_PASSWORD).andExpect(status().isOk()).andReturn();
    }

    private ResultActions login(String password) throws Exception {
        LoginRequest request = new LoginRequest(EMAIL, password);

        return mockMvc.perform(post(LOGIN_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
    }

    private String accessToken(MvcResult result) throws Exception {
        return objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asString();
    }

    private String refreshCookieValue(MvcResult result) {
        return result.getResponse().getCookie(REFRESH_COOKIE_NAME).getValue();
    }

    private String passwordHash() {
        return jdbcTemplate.queryForObject(
            "SELECT password_hash FROM app_user WHERE email = ?",
            String.class,
            EMAIL
        );
    }

    private Object revokedAt(String refreshToken) {
        return jdbcTemplate.queryForMap(
            "SELECT revoked_at FROM refresh_token WHERE token_hash = ?",
            sha256Hex(refreshToken)
        ).get("revoked_at");
    }

    private int countActiveTokens() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_token WHERE revoked_at IS NULL",
            Integer.class
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

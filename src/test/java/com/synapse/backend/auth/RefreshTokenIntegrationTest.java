package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.synapse.backend.auth.dto.LoginRequest;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String LOGIN_ENDPOINT = "/api/auth/login";
    private static final String REFRESH_ENDPOINT = "/api/auth/refresh";
    private static final String LOGOUT_ENDPOINT = "/api/auth/logout";
    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String VALID_PASSWORD = "password123";
    private static final String EMAIL = "kenneth@example.com";
    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired refresh token.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void registerSetsRefreshCookieAndStoresOnlyItsHash() throws Exception {
        RegisterRequest request = new RegisterRequest("Kenneth", EMAIL, VALID_PASSWORD);

        MvcResult result = mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE_NAME);
        Map<String, Object> savedToken = jdbcTemplate.queryForMap("SELECT * FROM refresh_token");

        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotEmpty();
        assertThat(savedToken.get("token_hash")).isEqualTo(sha256Hex(cookie.getValue()));
        assertThat(savedToken.get("token_hash")).isNotEqualTo(cookie.getValue());
        assertThat(savedToken.get("revoked_at")).isNull();
    }

    @Test
    void loginSetsSecureHttpOnlyRefreshCookieWithThirtyDayExpiry() throws Exception {
        long userId = createUser("Kenneth", EMAIL, VALID_PASSWORD);

        MvcResult result = login();

        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE_NAME);
        String setCookieHeader = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        Map<String, Object> savedToken = jdbcTemplate.queryForMap("SELECT * FROM refresh_token");

        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(setCookieHeader).contains("SameSite=None");
        assertThat(savedToken.get("user_id")).isEqualTo(userId);
        assertThat(((Timestamp) savedToken.get("expires_at")).toLocalDateTime())
            .isAfter(LocalDateTime.now().plusDays(29))
            .isBefore(LocalDateTime.now().plusDays(31));
    }

    @Test
    void refreshReturnsNewAccessTokenAndRotatesRefreshToken() throws Exception {
        long userId = createUser("Kenneth", EMAIL, VALID_PASSWORD);
        String refreshToken = refreshCookieValue(login());

        MvcResult result = mockMvc.perform(post(REFRESH_ENDPOINT).cookie(refreshCookie(refreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

        String rotatedToken = refreshCookieValue(result);
        Jwt jwt = jwtDecoder.decode(objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asString());
        Map<String, Object> oldRow = tokenRow(refreshToken);
        Map<String, Object> newRow = tokenRow(rotatedToken);

        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(jwt.getClaimAsString("email")).isEqualTo(EMAIL);
        assertThat(rotatedToken).isNotEqualTo(refreshToken);
        assertThat(oldRow.get("revoked_at")).isNotNull();
        assertThat(newRow.get("revoked_at")).isNull();
        assertThat(newRow.get("user_id")).isEqualTo(userId);
    }

    @Test
    void refreshRejectsRefreshTokenThatWasAlreadyRotated() throws Exception {
        createUser("Kenneth", EMAIL, VALID_PASSWORD);
        String refreshToken = refreshCookieValue(login());

        mockMvc.perform(post(REFRESH_ENDPOINT).cookie(refreshCookie(refreshToken)))
            .andExpect(status().isOk());

        mockMvc.perform(post(REFRESH_ENDPOINT).cookie(refreshCookie(refreshToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    void refreshRejectsExpiredRefreshToken() throws Exception {
        long userId = createUser("Kenneth", EMAIL, VALID_PASSWORD);
        String refreshToken = "expired-refresh-token";

        jdbcTemplate.update(
            "INSERT INTO refresh_token (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
            userId,
            sha256Hex(refreshToken),
            LocalDateTime.now().minusMinutes(1)
        );

        mockMvc.perform(post(REFRESH_ENDPOINT).cookie(refreshCookie(refreshToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));

        assertThat(tokenRow(refreshToken).get("revoked_at")).isNull();
    }

    @Test
    void refreshRejectsRevokedRefreshToken() throws Exception {
        long userId = createUser("Kenneth", EMAIL, VALID_PASSWORD);
        String refreshToken = "revoked-refresh-token";

        jdbcTemplate.update(
            "INSERT INTO refresh_token (user_id, token_hash, expires_at, revoked_at) VALUES (?, ?, ?, ?)",
            userId,
            sha256Hex(refreshToken),
            LocalDateTime.now().plusDays(30),
            LocalDateTime.now()
        );

        mockMvc.perform(post(REFRESH_ENDPOINT).cookie(refreshCookie(refreshToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    void refreshRejectsUnknownRefreshToken() throws Exception {
        createUser("Kenneth", EMAIL, VALID_PASSWORD);

        mockMvc.perform(post(REFRESH_ENDPOINT).cookie(refreshCookie("not-a-real-refresh-token")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    void refreshRejectsRequestWithoutRefreshCookie() throws Exception {
        mockMvc.perform(post(REFRESH_ENDPOINT))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    void logoutRevokesRefreshTokenClearsCookieAndBlocksFurtherRefresh() throws Exception {
        createUser("Kenneth", EMAIL, VALID_PASSWORD);
        String refreshToken = refreshCookieValue(login());

        MvcResult result = mockMvc.perform(post(LOGOUT_ENDPOINT).cookie(refreshCookie(refreshToken)))
            .andExpect(status().isNoContent())
            .andReturn();

        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE_NAME);

        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(tokenRow(refreshToken).get("revoked_at")).isNotNull();

        mockMvc.perform(post(REFRESH_ENDPOINT).cookie(refreshCookie(refreshToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    void concurrentRefreshWithTheSameTokenRotatesItOnlyOnce() throws Exception {
        long userId = createUser("Kenneth", EMAIL, VALID_PASSWORD);
        String refreshToken = refreshCookieValue(login());

        List<Integer> statuses = refreshConcurrently(refreshToken, 2);

        assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        assertThat(tokenRow(refreshToken).get("revoked_at")).isNotNull();
        assertThat(countTokens(userId, "revoked_at IS NULL")).isEqualTo(1);
        assertThat(countTokens(userId, "1 = 1")).isEqualTo(2);
    }

    @Test
    void logoutWithoutRefreshCookieSucceeds() throws Exception {
        mockMvc.perform(post(LOGOUT_ENDPOINT))
            .andExpect(status().isNoContent());
    }

    @Test
    void logoutDoesNotRevokeRefreshTokensOfOtherSessions() throws Exception {
        createUser("Kenneth", EMAIL, VALID_PASSWORD);
        String firstSessionToken = refreshCookieValue(login());
        String secondSessionToken = refreshCookieValue(login());

        mockMvc.perform(post(LOGOUT_ENDPOINT).cookie(refreshCookie(firstSessionToken)))
            .andExpect(status().isNoContent());

        assertThat(tokenRow(firstSessionToken).get("revoked_at")).isNotNull();
        assertThat(tokenRow(secondSessionToken).get("revoked_at")).isNull();

        mockMvc.perform(post(REFRESH_ENDPOINT).cookie(refreshCookie(secondSessionToken)))
            .andExpect(status().isOk());
    }

    private List<Integer> refreshConcurrently(String refreshToken, int attempts) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CyclicBarrier barrier = new CyclicBarrier(attempts);
        List<Future<Integer>> results = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            results.add(executor.submit(() -> {
                barrier.await();

                return mockMvc.perform(post(REFRESH_ENDPOINT).cookie(refreshCookie(refreshToken)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            }));
        }

        List<Integer> statuses = new ArrayList<>();

        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }

        executor.shutdown();

        return statuses;
    }

    private int countTokens(long userId, String condition) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND " + condition,
            Integer.class,
            userId
        );
    }

    private MvcResult login() throws Exception {
        LoginRequest request = new LoginRequest(EMAIL, VALID_PASSWORD);

        return mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();
    }

    private String refreshCookieValue(MvcResult result) {
        return result.getResponse().getCookie(REFRESH_COOKIE_NAME).getValue();
    }

    private Cookie refreshCookie(String refreshToken) {
        return new Cookie(REFRESH_COOKIE_NAME, refreshToken);
    }

    private Map<String, Object> tokenRow(String refreshToken) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM refresh_token WHERE token_hash = ?",
            sha256Hex(refreshToken)
        );

        assertThat(rows).hasSize(1);

        return rows.getFirst();
    }

    private String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private long createUser(String fullName, String email, String password) {
        String passwordHash = passwordEncoder.encode(password);

        jdbcTemplate.update(
            "INSERT INTO app_user (full_name, email, password_hash) VALUES (?, ?, ?)",
            fullName,
            email,
            passwordHash
        );

        return jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, email);
    }
}

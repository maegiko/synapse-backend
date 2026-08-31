package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.synapse.backend.security.jwt.JwtService;
import com.synapse.backend.support.PostgresIntegrationTest;
import com.synapse.backend.user.User;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenRollbackIntegrationTest extends PostgresIntegrationTest {

    private static final String REFRESH_ENDPOINT = "/api/auth/refresh";
    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_TOKEN = "rollback-refresh-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void failureWhileRotatingLeavesThePresentedRefreshTokenUsable() throws Exception {
        long userId = createUserWithRefreshToken();

        when(jwtService.generateAccessToken(any(User.class)))
            .thenThrow(new IllegalStateException("token signing failed"));

        assertThatThrownBy(() -> mockMvc.perform(post(REFRESH_ENDPOINT)
                .cookie(new Cookie(REFRESH_COOKIE_NAME, REFRESH_TOKEN))))
            .hasRootCauseInstanceOf(IllegalStateException.class);

        Map<String, Object> tokenRow = jdbcTemplate.queryForMap(
            "SELECT * FROM refresh_token WHERE token_hash = ?",
            sha256Hex(REFRESH_TOKEN)
        );
        Integer tokenCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_token WHERE user_id = ?",
            Integer.class,
            userId
        );

        assertThat(tokenRow.get("revoked_at")).isNull();
        assertThat(tokenCount).isEqualTo(1);
    }

    private long createUserWithRefreshToken() {
        jdbcTemplate.update(
            "INSERT INTO app_user (full_name, email, password_hash) VALUES (?, ?, ?)",
            "Kenneth",
            "kenneth@example.com",
            "not-a-real-hash"
        );

        Long userId = jdbcTemplate.queryForObject(
            "SELECT id FROM app_user WHERE email = ?",
            Long.class,
            "kenneth@example.com"
        );

        jdbcTemplate.update(
            "INSERT INTO refresh_token (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
            userId,
            sha256Hex(REFRESH_TOKEN),
            LocalDateTime.now(ZoneOffset.UTC).plusDays(30)
        );

        return userId;
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

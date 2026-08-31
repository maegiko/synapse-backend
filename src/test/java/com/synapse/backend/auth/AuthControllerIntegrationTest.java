package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.synapse.backend.auth.dto.LoginRequest;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String LOGIN_ENDPOINT = "/api/auth/login";
    private static final String VALID_PASSWORD = "password123";

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
    void registerCreatesUserAndReturnsAccessToken() throws Exception {
        RegisterRequest request = new RegisterRequest("Kenneth", "kenneth@example.com", VALID_PASSWORD);

        MvcResult result = mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fullName").value("Kenneth"))
            .andExpect(jsonPath("$.email").value("kenneth@example.com"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

        Map<String, Object> savedUser = jdbcTemplate.queryForMap(
            "SELECT id, full_name, email, password_hash FROM app_user WHERE email = ?",
            "kenneth@example.com"
        );
        String accessToken = objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asString();
        Jwt jwt = jwtDecoder.decode(accessToken);

        assertThat(savedUser.get("full_name")).isEqualTo("Kenneth");
        assertThat(savedUser.get("email")).isEqualTo("kenneth@example.com");
        assertThat(savedUser.get("password_hash")).isNotEqualTo(VALID_PASSWORD);
        assertThat(savedUser.get("password_hash").toString()).startsWith("$2");
        assertThat(jwt.getSubject()).isEqualTo(savedUser.get("id").toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("kenneth@example.com");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Kenneth");
    }

    @Test
    void registerStoresTheSuppliedTimeZone() throws Exception {
        RegisterRequest request =
            new RegisterRequest("Kenneth", "kenneth@example.com", VALID_PASSWORD, "Australia/Sydney");

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        assertThat(timeZoneOf("kenneth@example.com")).isEqualTo("Australia/Sydney");
    }

    @Test
    void registerTrimsTheSuppliedTimeZone() throws Exception {
        RegisterRequest request =
            new RegisterRequest("Kenneth", "kenneth@example.com", VALID_PASSWORD, "  Europe/London  ");

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        assertThat(timeZoneOf("kenneth@example.com")).isEqualTo("Europe/London");
    }

    @Test
    void registerFallsBackToUtcWhenNoTimeZoneIsSupplied() throws Exception {
        RegisterRequest request = new RegisterRequest("Kenneth", "kenneth@example.com", VALID_PASSWORD);

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        assertThat(timeZoneOf("kenneth@example.com")).isEqualTo("UTC");
    }

    @Test
    void registerFallsBackToUtcWhenTheTimeZoneIsBlank() throws Exception {
        RegisterRequest request =
            new RegisterRequest("Kenneth", "kenneth@example.com", VALID_PASSWORD, "   ");

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        assertThat(timeZoneOf("kenneth@example.com")).isEqualTo("UTC");
    }

    @Test
    void registerReturnsBadRequestWhenTheTimeZoneIsNotARealZone() throws Exception {
        RegisterRequest request =
            new RegisterRequest("Kenneth", "kenneth@example.com", VALID_PASSWORD, "Mars/Olympus_Mons");

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("timeZone: must be a valid IANA time zone"));

        assertThat(countUsers("kenneth@example.com")).isZero();
    }

    @Test
    void registerReturnsConflictWhenEmailAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest("Kenneth", "kenneth@example.com", VALID_PASSWORD);

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Email is already registered: kenneth@example.com"));
    }

    @Test
    void registerReturnsBadRequestWhenNameIsMissing() throws Exception {
        RegisterRequest request = new RegisterRequest(null, "kenneth@example.com", VALID_PASSWORD);

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("fullName: must not be blank"));
    }

    @Test
    void registerReturnsBadRequestWhenEmailIsInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest("Kenneth", "not-an-email", VALID_PASSWORD);

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("email: must be a well-formed email address"));
    }

    @Test
    void registerReturnsBadRequestWhenPasswordIsTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest("Kenneth", "kenneth@example.com", "short");

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("password: size must be between 8 and 64"));
    }

    @Test
    void loginReturnsUserAndAccessTokenForValidCredentials() throws Exception {
        long userId = createUser("Kenneth", "kenneth@example.com", VALID_PASSWORD);
        LoginRequest request = new LoginRequest("kenneth@example.com", VALID_PASSWORD);

        MvcResult result = mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Kenneth"))
            .andExpect(jsonPath("$.email").value("kenneth@example.com"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

        String accessToken = objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asString();
        Jwt jwt = jwtDecoder.decode(accessToken);

        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(jwt.getClaimAsString("email")).isEqualTo("kenneth@example.com");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Kenneth");
    }

    @Test
    void loginReturnsUnauthorizedWhenPasswordIsIncorrect() throws Exception {
        createUser("Kenneth", "kenneth@example.com", VALID_PASSWORD);
        LoginRequest request = new LoginRequest("kenneth@example.com", "wrong-password");

        mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void loginReturnsUnauthorizedWhenEmailDoesNotExist() throws Exception {
        LoginRequest request = new LoginRequest("missing@example.com", VALID_PASSWORD);

        mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password."));

        Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class);

        assertThat(userCount).isZero();
    }

    @Test
    void loginReturnsBadRequestWhenEmailIsMissing() throws Exception {
        LoginRequest request = new LoginRequest(null, VALID_PASSWORD);

        mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("email: must not be blank"));
    }

    @Test
    void loginReturnsBadRequestWhenEmailIsInvalid() throws Exception {
        LoginRequest request = new LoginRequest("not-an-email", VALID_PASSWORD);

        mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("email: must be a well-formed email address"));
    }

    @Test
    void loginReturnsBadRequestWhenPasswordIsMissing() throws Exception {
        LoginRequest request = new LoginRequest("kenneth@example.com", null);

        mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("password: must not be blank"));
    }

    @Test
    void loginReturnsBadRequestWhenPasswordIsTooShort() throws Exception {
        LoginRequest request = new LoginRequest("kenneth@example.com", "short");

        mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("password: size must be between 8 and 64"));
    }

    private String timeZoneOf(String email) {
        return jdbcTemplate.queryForObject(
            "SELECT time_zone FROM app_user WHERE email = ?",
            String.class,
            email
        );
    }

    private int countUsers(String email) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM app_user WHERE email = ?",
            Integer.class,
            email
        );
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

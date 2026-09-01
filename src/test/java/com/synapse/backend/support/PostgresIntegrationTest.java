package com.synapse.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.synapse.backend.auth.dto.LoginRequest;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.email.EmailClient;
import com.synapse.backend.shared.ratelimit.RateLimitService;

import tools.jackson.databind.ObjectMapper;

@SuppressWarnings("resource")
public abstract class PostgresIntegrationTest {

    private static final String JWT_TEST_SECRET = "test-jwt-secret-with-at-least-32-bytes";
    private static final String RESEND_TEST_API_KEY = "test-resend-api-key";
    private static final String RESEND_UNREACHABLE_API_URL = "http://localhost:1/emails";

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String LOGIN_ENDPOINT = "/api/auth/login";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16")
        .withDatabaseName("synapse_test")
        .withUsername("test_user")
        .withPassword("test_password");

    static {
        POSTGRES.start();
    }

    /**
     * Replaces the real Resend client for every integration test, so no test can
     * send email or reach the provider. Tests that care about what was sent read
     * this mock through {@link #emailClient()}.
     */
    @MockitoBean
    private EmailClient emailClient;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetRateLimits() {
        rateLimitService.reset();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("jwt.secret", () -> JWT_TEST_SECRET);
        registry.add("email.resend.api-key", () -> RESEND_TEST_API_KEY);
        registry.add("email.resend.api-url", () -> RESEND_UNREACHABLE_API_URL);
    }

    /** The mocked email provider boundary, so a test can stub or verify sends. */
    protected EmailClient emailClient() {
        return emailClient;
    }

    /**
     * Registers a user, verifies their email address, and logs them in.
     *
     * <p>Registration alone no longer returns an access token, so tests that are
     * not about verification use this to reach a normally authenticated user.</p>
     *
     * @param fullName the user's name.
     * @param email the user's email address.
     * @param password the user's password.
     * @return the access token of the verified user.
     */
    protected String registerAndAuthenticate(String fullName, String email, String password) throws Exception {
        return registerAndAuthenticate(fullName, email, password, null);
    }

    /**
     * Registers a user in a time zone, verifies their email address, and logs them in.
     *
     * @param fullName the user's name.
     * @param email the user's email address.
     * @param password the user's password.
     * @param timeZone the IANA time zone to register with, or null for the default.
     * @return the access token of the verified user.
     */
    protected String registerAndAuthenticate(
        String fullName,
        String email,
        String password,
        String timeZone
    ) throws Exception {
        registerVerifiedUser(fullName, email, password, timeZone);

        return authenticate(email, password);
    }

    /**
     * Registers a user through the public endpoint and marks their address verified,
     * without logging them in.
     *
     * @param fullName the user's name.
     * @param email the user's email address.
     * @param password the user's password.
     * @param timeZone the IANA time zone to register with, or null for the default.
     */
    protected void registerVerifiedUser(
        String fullName,
        String email,
        String password,
        String timeZone
    ) throws Exception {
        RegisterRequest request = new RegisterRequest(fullName, email, password, timeZone);

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted());

        markEmailVerified(email);
    }

    /**
     * Logs an existing verified user in.
     *
     * @param email the user's email address.
     * @param password the user's password.
     * @return the access token of the logged in user.
     */
    protected String authenticate(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);

        MvcResult result = mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asString();
    }

    /** Marks an account verified directly, the state a confirmed registration leaves it in. */
    protected void markEmailVerified(String email) {
        jdbcTemplate.update(
            "UPDATE app_user SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?",
            email
        );
    }

}

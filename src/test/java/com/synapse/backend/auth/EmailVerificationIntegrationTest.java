package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.auth.dto.LoginRequest;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.email.dto.EmailMessage;
import com.synapse.backend.email.exceptions.EmailProviderException;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class EmailVerificationIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String LOGIN_ENDPOINT = "/api/auth/login";
    private static final String VERIFY_ENDPOINT = "/api/auth/email/verify";
    private static final String EMAIL = "kenneth@example.com";
    private static final String VALID_PASSWORD = "password123";

    private static final String MIGRATION_FILE = "db/migration/V25__add_email_verification.sql";

    private static final Pattern LINK_PATTERN = Pattern.compile("verify-email\\?token=([A-Za-z0-9\\-_%]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void registrationSendsAVerificationEmailWithTheLinkRecipientAndExpiry() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());

        EmailMessage message = sentMessage();
        Map<String, Object> token = tokenRow();
        LocalDateTime expiresAt = ((Timestamp) token.get("expires_at")).toLocalDateTime();
        String expiry = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(expiresAt) + " UTC";

        assertThat(message.to()).isEqualTo(EMAIL);
        assertThat(message.subject()).isEqualTo("Verify your Synapse email address");
        assertThat(message.text()).contains("http://localhost:5173/verify-email?token=");
        assertThat(message.html()).contains("http://localhost:5173/verify-email?token=");
        assertThat(message.text()).contains(expiry);
        assertThat(message.html()).contains(expiry);
        assertThat(message.text()).contains("ignore this email");
        assertThat(message.idempotencyKey()).isEqualTo("email-verification-" + token.get("id"));
        assertThat(expiresAt)
            .isAfter(LocalDateTime.now(ZoneOffset.UTC).plusHours(23))
            .isBefore(LocalDateTime.now(ZoneOffset.UTC).plusHours(25));
        assertThat(token.get("purpose")).isEqualTo("REGISTRATION");
        assertThat(token.get("email")).isEqualTo(EMAIL);
    }

    @Test
    void anUnverifiedUserCannotLogInAndAVerifiedOneCan() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());

        login(EMAIL, VALID_PASSWORD)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message")
                .value("Email address is not verified. Check your inbox for the verification link."));

        verifyEmail(rawTokenFromEmail()).andExpect(status().isNoContent());

        login(EMAIL, VALID_PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void confirmingRegistrationMarksTheAccountVerifiedAndConsumesTheToken() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());

        verifyEmail(rawTokenFromEmail()).andExpect(status().isNoContent());

        assertThat(emailVerifiedAt(EMAIL)).isNotNull();
        assertThat(tokenRow().get("consumed_at")).isNotNull();
    }

    @Test
    void confirmingRegistrationDoesNotLogTheUserIn() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());

        verifyEmail(rawTokenFromEmail())
            .andExpect(status().isNoContent())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty())
            .andExpect(result -> assertThat(result.getResponse().getCookie("refreshToken")).isNull());

        assertThat(countRefreshTokens()).isZero();
    }

    @Test
    void theMigrationBackfillMarksEveryExistingAccountVerified() throws Exception {
        // A user as they looked before verification existed: no confirmation, and an account
        // that has been in use for months.
        jdbcTemplate.update(
            "INSERT INTO app_user (full_name, email, password_hash) VALUES ('Kenneth', ?, ?)",
            EMAIL,
            passwordEncoder.encode(VALID_PASSWORD)
        );

        assertThat(emailVerifiedAt(EMAIL)).isNull();

        runMigrationBackfill();

        assertThat(emailVerifiedAt(EMAIL)).isNotNull();

        login(EMAIL, VALID_PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void aFailedEmailProviderReturnsBadGatewayAndLeavesARecoverableAccount() throws Exception {
        doThrow(new EmailProviderException("Failed to send the email through the email provider"))
            .when(emailClient()).send(any(EmailMessage.class));

        register(EMAIL)
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.message").value("Failed to send the email through the email provider"));

        assertThat(countUsers(EMAIL)).isEqualTo(1);
        assertThat(emailVerifiedAt(EMAIL)).isNull();
        assertThat(countTokens()).isEqualTo(1);
    }

    @Test
    void anAccountLeftBehindByAFailedSendIsRecoveredByResending() throws Exception {
        doThrow(new EmailProviderException("Failed to send the email through the email provider"))
            .when(emailClient()).send(any(EmailMessage.class));

        register(EMAIL).andExpect(status().isBadGateway());

        reset(emailClient());

        mockMvc.perform(post("/api/auth/email/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + EMAIL + "\"}"))
            .andExpect(status().isNoContent());

        verifyEmail(rawTokenFromEmail()).andExpect(status().isNoContent());

        login(EMAIL, VALID_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void aProviderFailureNeverExposesTheApiKeyOrProviderInternals() throws Exception {
        doThrow(new EmailProviderException("Failed to send the email through the email provider"))
            .when(emailClient()).send(any(EmailMessage.class));

        String body = register(EMAIL)
            .andExpect(status().isBadGateway())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(body).doesNotContain("test-resend-api-key");
        assertThat(body).doesNotContain("resend.com");
    }

    @Test
    void aFailedRegistrationValidationSendsNothing() throws Exception {
        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new RegisterRequest("Kenneth", "not-an-email", VALID_PASSWORD))))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(emailClient());
    }

    /**
     * Replays the data statement of the verification migration against a row seeded to look the
     * way it did before it, reading it from the real migration file rather than copying it here.
     */
    private void runMigrationBackfill() {
        String sql;

        try (InputStream migration = new ClassPathResource(MIGRATION_FILE).getInputStream()) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (String statement : sql.replaceAll("(?m)^--.*$", "").split(";")) {
            if (statement.strip().toUpperCase(Locale.ROOT).startsWith("UPDATE"))
                jdbcTemplate.execute(statement);
        }
    }

    private ResultActions register(String email) throws Exception {
        return mockMvc.perform(post(REGISTER_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new RegisterRequest("Kenneth", email, VALID_PASSWORD))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post(LOGIN_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new LoginRequest(email, password))));
    }

    private ResultActions verifyEmail(String token) throws Exception {
        return mockMvc.perform(post(VERIFY_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("token", token))));
    }

    private EmailMessage sentMessage() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);

        verify(emailClient()).send(captor.capture());

        return captor.getValue();
    }

    /** Reads the raw token back out of the emailed link, the way the frontend page does. */
    private String rawTokenFromEmail() {
        Matcher matcher = LINK_PATTERN.matcher(sentMessage().text());

        assertThat(matcher.find()).isTrue();

        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    private Map<String, Object> tokenRow() {
        return jdbcTemplate.queryForMap("SELECT * FROM email_verification_token");
    }

    private Object emailVerifiedAt(String email) {
        return jdbcTemplate.queryForMap("SELECT email_verified_at FROM app_user WHERE email = ?", email)
            .get("email_verified_at");
    }

    private int countUsers(String email) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user WHERE email = ?", Integer.class, email);
    }

    private int countTokens() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM email_verification_token", Integer.class);
    }

    private int countRefreshTokens() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_token", Integer.class);
    }

}

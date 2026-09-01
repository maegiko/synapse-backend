package com.synapse.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

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
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.email.dto.EmailMessage;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class EmailChangeIntegrationTest extends PostgresIntegrationTest {

    private static final String EMAIL_CHANGE_ENDPOINT = "/api/user/email-change";
    private static final String USER_DETAILS_ENDPOINT = "/api/user/details";
    private static final String VERIFY_ENDPOINT = "/api/auth/email/verify";
    private static final String EMAIL = "kenneth@example.com";
    private static final String NEW_EMAIL = "kenneth.koon@example.com";
    private static final String VALID_PASSWORD = "password123";
    private static final int EMAIL_CHANGES_PER_HOUR = 3;

    private static final Pattern LINK_PATTERN = Pattern.compile("verify-email\\?token=([A-Za-z0-9\\-_%]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void requestingAChangeEmailsTheProposedAddressAndLeavesTheAccountAlone() throws Exception {
        String accessToken = registerAndAuthenticate("Kenneth", EMAIL, VALID_PASSWORD);
        reset(emailClient());

        requestEmailChange(accessToken, NEW_EMAIL)
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.pendingEmail").value(NEW_EMAIL))
            .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        EmailMessage message = lastMessage();

        assertThat(message.to()).isEqualTo(NEW_EMAIL);
        assertThat(message.subject()).isEqualTo("Confirm your new Synapse email address");
        assertThat(message.text()).contains("http://localhost:5173/verify-email?token=");
        assertThat(message.text()).contains("ignore this email");
        assertThat(emailOf()).isEqualTo(EMAIL);
        assertThat(tokenRow().get("purpose")).isEqualTo("EMAIL_CHANGE");
        assertThat(tokenRow().get("email")).isEqualTo(NEW_EMAIL);
    }

    @Test
    void theCurrentEmailStillWorksWhileAChangeIsPending() throws Exception {
        String accessToken = registerAndAuthenticate("Kenneth", EMAIL, VALID_PASSWORD);

        requestEmailChange(accessToken, NEW_EMAIL).andExpect(status().isAccepted());

        authenticate(EMAIL, VALID_PASSWORD);

        mockMvc.perform(get(USER_DETAILS_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void confirmingTheChangeMovesTheAccountToTheNewAddress() throws Exception {
        String accessToken = registerAndAuthenticate("Kenneth", EMAIL, VALID_PASSWORD);
        reset(emailClient());

        requestEmailChange(accessToken, NEW_EMAIL).andExpect(status().isAccepted());
        verifyEmail(rawTokenFromLastEmail()).andExpect(status().isNoContent());

        assertThat(emailOf()).isEqualTo(NEW_EMAIL);
        assertThat(countUsers(EMAIL)).isZero();
        assertThat(verifiedAt(NEW_EMAIL)).isNotNull();
    }

    @Test
    void theOldAddressStopsLoggingInAndTheNewOneStartsAfterConfirmation() throws Exception {
        String accessToken = registerAndAuthenticate("Kenneth", EMAIL, VALID_PASSWORD);
        reset(emailClient());

        requestEmailChange(accessToken, NEW_EMAIL).andExpect(status().isAccepted());
        verifyEmail(rawTokenFromLastEmail()).andExpect(status().isNoContent());

        login(EMAIL, VALID_PASSWORD)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password."));

        login(NEW_EMAIL, VALID_PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(NEW_EMAIL));
    }

    @Test
    void anAbandonedChangeLeavesTheOldAddressUntouched() throws Exception {
        String accessToken = registerAndAuthenticate("Kenneth", EMAIL, VALID_PASSWORD);

        requestEmailChange(accessToken, NEW_EMAIL).andExpect(status().isAccepted());

        assertThat(emailOf()).isEqualTo(EMAIL);

        login(EMAIL, VALID_PASSWORD).andExpect(status().isOk());
        assertThat(countUsers(NEW_EMAIL)).isZero();
    }

    @Test
    void anExpiredChangeIsInertAndCanBeReplaced() throws Exception {
        String accessToken = registerAndAuthenticate("Kenneth", EMAIL, VALID_PASSWORD);
        reset(emailClient());

        requestEmailChange(accessToken, NEW_EMAIL).andExpect(status().isAccepted());
        String expiredToken = rawTokenFromLastEmail();

        jdbcTemplate.update(
            "UPDATE email_verification_token SET expires_at = ? WHERE purpose = 'EMAIL_CHANGE'",
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)
        );

        verifyEmail(expiredToken)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid or expired verification token."));

        assertThat(emailOf()).isEqualTo(EMAIL);

        requestEmailChange(accessToken, NEW_EMAIL).andExpect(status().isAccepted());
        verifyEmail(rawTokenFromLastEmail()).andExpect(status().isNoContent());

        assertThat(emailOf()).isEqualTo(NEW_EMAIL);
    }

    @Test
    void aNewRequestInvalidatesThePreviousOne() throws Exception {
        String accessToken = registerAndAuthenticate("Kenneth", EMAIL, VALID_PASSWORD);
        reset(emailClient());

        requestEmailChange(accessToken, NEW_EMAIL).andExpect(status().isAccepted());
        String firstToken = rawTokenFromLastEmail();

        requestEmailChange(accessToken, "third@example.com").andExpect(status().isAccepted());
        String secondToken = rawTokenFromLastEmail();

        verifyEmail(firstToken)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid or expired verification token."));

        assertThat(emailOf()).isEqualTo(EMAIL);

        verifyEmail(secondToken).andExpect(status().isNoContent());

        assertThat(emailOf()).isEqualTo("third@example.com");
    }

    @Test
    void requestingTheAddressOfAnotherAccountIsRejectedAndSendsNothing() throws Exception {
        String accessToken = registerAndAuthenticate("Kenneth", EMAIL, VALID_PASSWORD);
        registerVerifiedUser("Ada", NEW_EMAIL, VALID_PASSWORD, null);
        reset(emailClient());

        requestEmailChange(accessToken, NEW_EMAIL)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Email is already registered: " + NEW_EMAIL));

        verifyNoInteractions(emailClient());
        assertThat(emailOf()).isEqualTo(EMAIL);
    }

    @Test
    void uniquenessIsCheckedAgainAtConfirmation() throws Exception {
        String accessToken = registerAndAuthenticate("Kenneth", EMAIL, VALID_PASSWORD);
        reset(emailClient());

        requestEmailChange(accessToken, NEW_EMAIL).andExpect(status().isAccepted());

        String token = rawTokenFromLastEmail();

        // Somebody else claims the address between the request and the confirmation.
        registerVerifiedUser("Ada", NEW_EMAIL, VALID_PASSWORD, null);

        verifyEmail(token)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Email is already registered: " + NEW_EMAIL));

        assertThat(emailOf()).isEqualTo(EMAIL);
    }

    @Test
    void aUniquenessRaceAtConfirmationIsAConflictNotAServerError() throws Exception {
        String accessToken = registerAndAuthenticate("Kenneth", EMAIL, VALID_PASSWORD);
        reset(emailClient());

        requestEmailChange(accessToken, NEW_EMAIL).andExpect(status().isAccepted());

        String token = rawTokenFromLastEmail();
        CountDownLatch inserted = new CountDownLatch(1);
        Thread claimant = claimEmailInAnUncommittedTransaction(NEW_EMAIL, inserted);

        assertThat(inserted.await(5, TimeUnit.SECONDS)).isTrue();

        verifyEmail(token)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Email is already registered: " + NEW_EMAIL));

        claimant.join();

        assertThat(emailOf()).isEqualTo(EMAIL);
        assertThat(countUsers(NEW_EMAIL)).isEqualTo(1);
    }

    @Test
    void proposingTheAddressTheUserAlreadyHasIsASafeNoOp() throws Exception {
        String accessToken = registerAndAuthenticate("Kenneth", EMAIL, VALID_PASSWORD);
        reset(emailClient());

        requestEmailChange(accessToken, "KENNETH@Example.com")
            .andExpect(status().isNoContent());

        verifyNoInteractions(emailClient());
        assertThat(emailOf()).isEqualTo(EMAIL);
        assertThat(countEmailChangeTokens()).isZero();
    }

    @Test
    void oneUsersChangeNeverAffectsAnotherAccount() throws Exception {
        String accessToken = registerAndAuthenticate("Kenneth", EMAIL, VALID_PASSWORD);
        registerVerifiedUser("Ada", "ada@example.com", VALID_PASSWORD, null);
        reset(emailClient());

        requestEmailChange(accessToken, NEW_EMAIL).andExpect(status().isAccepted());
        verifyEmail(rawTokenFromLastEmail()).andExpect(status().isNoContent());

        assertThat(countUsers("ada@example.com")).isEqualTo(1);
        assertThat(verifiedAt("ada@example.com")).isNotNull();
        login("ada@example.com", VALID_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void requestingAChangeReturnsBadRequestForAnInvalidAddress() throws Exception {
        String accessToken = registerAndAuthenticate("Kenneth", EMAIL, VALID_PASSWORD);
        reset(emailClient());

        requestEmailChange(accessToken, "not-an-email")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("email: must be a well-formed email address"));

        verifyNoInteractions(emailClient());
    }

    @Test
    void requestingAChangeRequiresAuthentication() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);
        reset(emailClient());

        mockMvc.perform(post(EMAIL_CHANGE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", NEW_EMAIL))))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(emailClient());
        assertThat(emailOf()).isEqualTo(EMAIL);
    }

    @Test
    void emailChangeLimitAppliesPerUser() throws Exception {
        String accessToken = registerAndAuthenticate("Kenneth", EMAIL, VALID_PASSWORD);
        String otherAccessToken = registerAndAuthenticate("Ada", "ada@example.com", VALID_PASSWORD);
        reset(emailClient());

        for (int i = 0; i < EMAIL_CHANGES_PER_HOUR; i++) {
            requestEmailChange(accessToken, "kenneth" + i + "@example.com")
                .andExpect(status().isAccepted());
        }

        requestEmailChange(accessToken, "kenneth-blocked@example.com")
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.message").value(startsWith("Too many requests.")));

        requestEmailChange(otherAccessToken, "ada.new@example.com")
            .andExpect(status().isAccepted());
    }

    @Test
    void theProposedAddressIsNormalisedBeforeItIsUsed() throws Exception {
        String accessToken = registerAndAuthenticate("Kenneth", EMAIL, VALID_PASSWORD);
        reset(emailClient());

        requestEmailChange(accessToken, "  Kenneth.KOON@Example.com  ")
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.pendingEmail").value(NEW_EMAIL));

        assertThat(lastMessage().to()).isEqualTo(NEW_EMAIL);

        verifyEmail(rawTokenFromLastEmail()).andExpect(status().isNoContent());

        assertThat(emailOf()).isEqualTo(NEW_EMAIL);
    }

    private ResultActions requestEmailChange(String accessToken, String email) throws Exception {
        return mockMvc.perform(post(EMAIL_CHANGE_ENDPOINT)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions verifyEmail(String token) throws Exception {
        return mockMvc.perform(post(VERIFY_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("token", token))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private EmailMessage lastMessage() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);

        verify(emailClient(), atLeastOnce()).send(captor.capture());

        return captor.getValue();
    }

    private String rawTokenFromLastEmail() {
        Matcher matcher = LINK_PATTERN.matcher(lastMessage().text());

        assertThat(matcher.find()).isTrue();

        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    /**
     * Inserts a user holding the address in an open transaction, so the confirmation passes its
     * own uniqueness check and then meets the unique constraint when it writes.
     */
    private Thread claimEmailInAnUncommittedTransaction(String email, CountDownLatch inserted) {
        Thread claimant = new Thread(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);

                try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO app_user (full_name, email, password_hash) VALUES (?, ?, ?)"
                )) {
                    statement.setString(1, "Someone");
                    statement.setString(2, email);
                    statement.setString(3, "hash");
                    statement.executeUpdate();
                }

                inserted.countDown();
                Thread.sleep(500);
                connection.commit();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });

        claimant.start();

        return claimant;
    }

    private Map<String, Object> tokenRow() {
        return jdbcTemplate.queryForMap("SELECT * FROM email_verification_token WHERE purpose = 'EMAIL_CHANGE'");
    }

    private String emailOf() {
        return jdbcTemplate.queryForObject(
            "SELECT email FROM app_user WHERE full_name = 'Kenneth'",
            String.class
        );
    }

    private Object verifiedAt(String email) {
        return jdbcTemplate.queryForMap("SELECT email_verified_at FROM app_user WHERE email = ?", email)
            .get("email_verified_at");
    }

    private int countUsers(String email) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user WHERE email = ?", Integer.class, email);
    }

    private int countEmailChangeTokens() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM email_verification_token WHERE purpose = 'EMAIL_CHANGE'",
            Integer.class
        );
    }

}

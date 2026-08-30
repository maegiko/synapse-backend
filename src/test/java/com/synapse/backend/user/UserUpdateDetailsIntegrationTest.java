package com.synapse.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.support.PostgresIntegrationTest;
import com.synapse.backend.user.dto.UpdateUserDetailsRequest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class UserUpdateDetailsIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String USER_DETAILS_ENDPOINT = "/api/user/details";
    private static final String VALID_PASSWORD = "password123";
    private static final String EMAIL = "kenneth@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private javax.sql.DataSource dataSource;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void updateUserDetailsUpdatesOnlyFullName() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest("Kenneth Koon", null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Kenneth Koon"))
            .andExpect(jsonPath("$.email").value(EMAIL))
            .andExpect(jsonPath("$.totalFlashcardsReviewed").value(0));

        Map<String, Object> savedUser = userRow(EMAIL);

        assertThat(savedUser.get("full_name")).isEqualTo("Kenneth Koon");
        assertThat(savedUser.get("email")).isEqualTo(EMAIL);
    }

    @Test
    void updateUserDetailsUpdatesOnlyEmail() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest(null, "new@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Kenneth"))
            .andExpect(jsonPath("$.email").value("new@example.com"));

        Map<String, Object> savedUser = userRow("new@example.com");

        assertThat(savedUser.get("full_name")).isEqualTo("Kenneth");
        assertThat(countUsers(EMAIL)).isZero();
    }

    @Test
    void updateUserDetailsUpdatesBothFields() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest("Kenneth Koon", "new@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Kenneth Koon"))
            .andExpect(jsonPath("$.email").value("new@example.com"));

        Map<String, Object> savedUser = userRow("new@example.com");

        assertThat(savedUser.get("full_name")).isEqualTo("Kenneth Koon");
    }

    @Test
    void updateUserDetailsTrimsFullNameAndNormalisesEmail() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest("  Kenneth Koon  ", "  NEW@Example.COM  "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Kenneth Koon"))
            .andExpect(jsonPath("$.email").value("new@example.com"));

        Map<String, Object> savedUser = userRow("new@example.com");

        assertThat(savedUser.get("full_name")).isEqualTo("Kenneth Koon");
    }

    @Test
    void updateUserDetailsAcceptsTheEmailTheUserAlreadyHas() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest("Kenneth Koon", "  KENNETH@Example.com  "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Kenneth Koon"))
            .andExpect(jsonPath("$.email").value(EMAIL));

        assertThat(userRow(EMAIL).get("full_name")).isEqualTo("Kenneth Koon");
    }

    @Test
    void updateUserDetailsReturnsConflictWhenEmailBelongsToAnotherUser() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);
        registerAndGetAccessToken("Someone", "someone@example.com");

        updateDetails(accessToken, new UpdateUserDetailsRequest("Kenneth Koon", "someone@example.com"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Email is already registered: someone@example.com"));

        Map<String, Object> savedUser = userRow(EMAIL);

        assertThat(savedUser.get("full_name")).isEqualTo("Kenneth");
    }

    @Test
    void updateUserDetailsReturnsConflictWhenAnotherUserClaimsTheEmailConcurrently() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);
        CountDownLatch inserted = new CountDownLatch(1);
        Thread claimant = claimEmailInAnUncommittedTransaction("shared@example.com", inserted);

        assertThat(inserted.await(5, TimeUnit.SECONDS)).isTrue();

        updateDetails(accessToken, new UpdateUserDetailsRequest(null, "shared@example.com"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Email is already registered: shared@example.com"));

        claimant.join();

        assertThat(countUsers("shared@example.com")).isEqualTo(1);
        assertThat(userRow(EMAIL).get("email")).isEqualTo(EMAIL);
    }

    @Test
    void updateUserDetailsReturnsBadRequestWhenNoFieldIsSupplied() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        mockMvc.perform(patch(USER_DETAILS_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("At least one of fullName or email must be supplied."));
    }

    @Test
    void updateUserDetailsReturnsBadRequestWhenFullNameIsBlank() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest("   ", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("fullName: size must be between 2 and 100"));

        assertThat(userRow(EMAIL).get("full_name")).isEqualTo("Kenneth");
    }

    @Test
    void updateUserDetailsReturnsBadRequestWhenFullNameIsTooShortOnceTrimmed() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest(" K ", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("fullName: size must be between 2 and 100"));

        assertThat(userRow(EMAIL).get("full_name")).isEqualTo("Kenneth");
    }

    @Test
    void updateUserDetailsAcceptsAnEmailPaddedWithWhitespace() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest(null, "   new@example.com   "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("new@example.com"));

        assertThat(userRow("new@example.com").get("email")).isEqualTo("new@example.com");
    }

    @Test
    void updateUserDetailsReturnsBadRequestWhenFullNameIsTooShort() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest("K", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("fullName: size must be between 2 and 100"));
    }

    @Test
    void updateUserDetailsReturnsBadRequestWhenEmailIsInvalid() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest(null, "not-an-email"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("email: must be a well-formed email address"));

        assertThat(userRow(EMAIL).get("email")).isEqualTo(EMAIL);
    }

    @Test
    void updateUserDetailsReturnsBadRequestWhenEmailIsEmpty() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest(null, ""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("email: must not be blank"));
    }

    @Test
    void updateUserDetailsReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        registerAndGetAccessToken("Kenneth", EMAIL);

        mockMvc.perform(patch(USER_DETAILS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateUserDetailsRequest("Kenneth Koon", null))))
            .andExpect(status().isUnauthorized());

        assertThat(userRow(EMAIL).get("full_name")).isEqualTo("Kenneth");
    }

    @Test
    void getUserDetailsReturnsTheUpdatedDetails() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest("Kenneth Koon", "new@example.com"))
            .andExpect(status().isOk());

        mockMvc.perform(get(USER_DETAILS_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Kenneth Koon"))
            .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    private ResultActions updateDetails(String accessToken, UpdateUserDetailsRequest request) throws Exception {
        return mockMvc.perform(patch(USER_DETAILS_ENDPOINT)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
    }

    /**
     * Inserts a user holding the email in an open transaction, so the request under test passes its
     * own existence check and then meets the unique constraint when it writes.
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

    private Map<String, Object> userRow(String email) {
        return jdbcTemplate.queryForMap(
            "SELECT id, full_name, email FROM app_user WHERE email = ?",
            email
        );
    }

    private int countUsers(String email) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user WHERE email = ?", Integer.class, email);
    }

    private String registerAndGetAccessToken(String fullName, String email) throws Exception {
        RegisterRequest request = new RegisterRequest(fullName, email, VALID_PASSWORD);

        MvcResult result = mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asString();
    }
}

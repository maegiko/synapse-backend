package com.synapse.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.support.PostgresIntegrationTest;
import com.synapse.backend.user.dto.UpdateUserDetailsRequest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class UserUpdateDetailsIntegrationTest extends PostgresIntegrationTest {

    private static final String USER_DETAILS_ENDPOINT = "/api/user/details";
    private static final String VALID_PASSWORD = "password123";
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
    void updateUserDetailsTrimsFullName() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest("  Kenneth Koon  ", null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Kenneth Koon"));

        assertThat(userRow(EMAIL).get("full_name")).isEqualTo("Kenneth Koon");
    }

    @Test
    void updateUserDetailsIgnoresASuppliedEmailAndLeavesTheAddressAlone() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        mockMvc.perform(patch(USER_DETAILS_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\": \"Kenneth Koon\", \"email\": \"new@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Kenneth Koon"))
            .andExpect(jsonPath("$.email").value(EMAIL));

        assertThat(userRow(EMAIL).get("email")).isEqualTo(EMAIL);
        assertThat(countUsers("new@example.com")).isZero();
    }

    @Test
    void updateUserDetailsReturnsBadRequestWhenOnlyAnEmailIsSupplied() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        mockMvc.perform(patch(USER_DETAILS_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"new@example.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                .value("At least one of fullName or timeZone must be supplied."));

        assertThat(userRow(EMAIL).get("email")).isEqualTo(EMAIL);
    }

    @Test
    void updateUserDetailsUpdatesOnlyTimeZone() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest(null, "Australia/Sydney"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Kenneth"))
            .andExpect(jsonPath("$.email").value(EMAIL))
            .andExpect(jsonPath("$.timeZone").value("Australia/Sydney"));

        assertThat(userRow(EMAIL).get("time_zone")).isEqualTo("Australia/Sydney");
    }

    @Test
    void updateUserDetailsUpdatesTimeZoneAlongsideTheFullName() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest("Kenneth Koon", "Europe/London"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Kenneth Koon"))
            .andExpect(jsonPath("$.email").value(EMAIL))
            .andExpect(jsonPath("$.timeZone").value("Europe/London"));

        assertThat(userRow(EMAIL).get("time_zone")).isEqualTo("Europe/London");
    }

    @Test
    void updateUserDetailsTrimsTheTimeZone() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest(null, "  Asia/Tokyo  "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeZone").value("Asia/Tokyo"));

        assertThat(userRow(EMAIL).get("time_zone")).isEqualTo("Asia/Tokyo");
    }

    @Test
    void updateUserDetailsReturnsBadRequestWhenTheTimeZoneIsNotARealZone() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest(null, "Mars/Olympus_Mons"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("timeZone: must be a valid IANA time zone"));

        assertThat(userRow(EMAIL).get("time_zone")).isEqualTo("UTC");
    }

    @Test
    void updateUserDetailsReturnsBadRequestWhenTheTimeZoneIsBlank() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest(null, "   "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("timeZone: must be a valid IANA time zone"));

        assertThat(userRow(EMAIL).get("time_zone")).isEqualTo("UTC");
    }

    @Test
    void updateUserDetailsOnlyChangesTheTimeZoneOfTheAuthenticatedUser() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);
        registerAndGetAccessToken("Ada", "ada@example.com");

        updateDetails(accessToken, new UpdateUserDetailsRequest(null, "Australia/Sydney"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeZone").value("Australia/Sydney"));

        assertThat(userRow(EMAIL).get("time_zone")).isEqualTo("Australia/Sydney");
        assertThat(userRow("ada@example.com").get("time_zone")).isEqualTo("UTC");
    }

    @Test
    void updateUserDetailsReturnsBadRequestWhenNoFieldIsSupplied() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        mockMvc.perform(patch(USER_DETAILS_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                .value("At least one of fullName or timeZone must be supplied."));
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
    void updateUserDetailsReturnsBadRequestWhenFullNameIsTooShort() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", EMAIL);

        updateDetails(accessToken, new UpdateUserDetailsRequest("K", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("fullName: size must be between 2 and 100"));
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

        updateDetails(accessToken, new UpdateUserDetailsRequest("Kenneth Koon", "Europe/London"))
            .andExpect(status().isOk());

        mockMvc.perform(get(USER_DETAILS_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Kenneth Koon"))
            .andExpect(jsonPath("$.email").value(EMAIL))
            .andExpect(jsonPath("$.timeZone").value("Europe/London"));
    }

    private ResultActions updateDetails(String accessToken, UpdateUserDetailsRequest request) throws Exception {
        return mockMvc.perform(patch(USER_DETAILS_ENDPOINT)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
    }

    private Map<String, Object> userRow(String email) {
        return jdbcTemplate.queryForMap(
            "SELECT id, full_name, email, time_zone FROM app_user WHERE email = ?",
            email
        );
    }

    private int countUsers(String email) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user WHERE email = ?", Integer.class, email);
    }

    private String registerAndGetAccessToken(String fullName, String email) throws Exception {
        return registerAndAuthenticate(fullName, email, VALID_PASSWORD);
    }
}

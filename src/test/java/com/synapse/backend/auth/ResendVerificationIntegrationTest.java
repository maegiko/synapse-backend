package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.email.dto.EmailMessage;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ResendVerificationIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String RESEND_ENDPOINT = "/api/auth/email/resend";
    private static final String EMAIL = "kenneth@example.com";
    private static final String VALID_PASSWORD = "password123";

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
    void resendingToAnUnverifiedAccountSendsAReplacementLink() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());

        resend(EMAIL).andExpect(status().isNoContent());

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);

        verify(emailClient(), times(2)).send(captor.capture());

        assertThat(captor.getAllValues()).allSatisfy(message -> {
            assertThat(message.to()).isEqualTo(EMAIL);
            assertThat(message.subject()).isEqualTo("Verify your Synapse email address");
        });
        assertThat(captor.getAllValues().get(0).text()).isNotEqualTo(captor.getAllValues().get(1).text());
        assertThat(captor.getAllValues().get(0).idempotencyKey())
            .isNotEqualTo(captor.getAllValues().get(1).idempotencyKey());
    }

    @Test
    void resendingToAnUnknownAddressLooksIdenticalAndSendsNothing() throws Exception {
        MvcResult result = resend("nobody@example.com")
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        verifyNoInteractions(emailClient());
    }

    @Test
    void resendingToAVerifiedAccountLooksIdenticalAndSendsNothing() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);
        reset(emailClient());

        resend(EMAIL).andExpect(status().isNoContent());

        verifyNoInteractions(emailClient());
        assertThat(countTokensOf(EMAIL)).isEqualTo(1);
    }

    @Test
    void everyResendOutcomeReturnsTheSameResponse() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());
        registerVerifiedUser("Ada", "ada@example.com", VALID_PASSWORD, null);

        assertThat(statusOf(resend(EMAIL))).isEqualTo(204);
        assertThat(statusOf(resend("ada@example.com"))).isEqualTo(204);
        assertThat(statusOf(resend("nobody@example.com"))).isEqualTo(204);
    }

    @Test
    void resendNormalisesTheEmailLikeRegistrationAndLogin() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());
        reset(emailClient());

        resend("KENNETH@Example.COM").andExpect(status().isNoContent());

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);

        verify(emailClient()).send(captor.capture());

        assertThat(captor.getValue().to()).isEqualTo(EMAIL);
    }

    @Test
    void resendDoesNotTouchTheStoredPasswordOrProfile() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());

        Map<String, Object> before = userRow();

        resend(EMAIL).andExpect(status().isNoContent());

        assertThat(userRow()).isEqualTo(before);
    }

    @Test
    void resendReturnsBadRequestForAnInvalidAddressAndSendsNothing() throws Exception {
        mockMvc.perform(post(RESEND_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"not-an-email\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("email: must be a well-formed email address"));

        verifyNoInteractions(emailClient());
    }

    @Test
    void resendRequiresNoAuthentication() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());

        resend(EMAIL).andExpect(status().isNoContent());
    }

    private int statusOf(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getStatus();
    }

    private ResultActions register(String email) throws Exception {
        return mockMvc.perform(post(REGISTER_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new RegisterRequest("Kenneth", email, VALID_PASSWORD))));
    }

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(post(RESEND_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }

    private Map<String, Object> userRow() {
        return jdbcTemplate.queryForMap(
            "SELECT full_name, email, password_hash, time_zone, email_verified_at FROM app_user WHERE email = ?",
            EMAIL
        );
    }

    private int countTokensOf(String email) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM email_verification_token WHERE email = ?",
            Integer.class,
            email
        );
    }

}

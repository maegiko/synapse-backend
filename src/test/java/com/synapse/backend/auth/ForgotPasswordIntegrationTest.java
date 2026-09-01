package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.email.dto.EmailMessage;
import com.synapse.backend.email.exceptions.EmailProviderException;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

/**
 * What asking for a reset link does, and what it deliberately never tells the
 * caller. Every outcome the endpoint can have has to be indistinguishable from
 * the outside, so most of these tests assert the same 204 for a different state.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ForgotPasswordIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String FORGOT_ENDPOINT = "/api/auth/password/forgot";
    private static final String EMAIL = "kenneth@example.com";
    private static final String VALID_PASSWORD = "password123";
    private static final int RESETS_PER_HOUR = 3;

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
    void aVerifiedAccountIsSentAResetLinkWithTheExpiryAndAnIgnoreInstruction() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);
        reset(emailClient());

        forgot(EMAIL).andExpect(status().isNoContent());

        EmailMessage message = lastMessage();
        Map<String, Object> token = tokenRow();
        LocalDateTime expiresAt = ((Timestamp) token.get("expires_at")).toLocalDateTime();
        String expiry = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(expiresAt) + " UTC";

        assertThat(message.to()).isEqualTo(EMAIL);
        assertThat(message.subject()).isEqualTo("Reset your Synapse password");
        assertThat(message.text()).contains("http://localhost:5173/reset-password?token=");
        assertThat(message.html()).contains("http://localhost:5173/reset-password?token=");
        assertThat(message.text()).contains(expiry);
        assertThat(message.html()).contains(expiry);
        assertThat(message.text()).contains("ignore this email");
        assertThat(message.html()).contains("ignore this email");
        assertThat(message.idempotencyKey()).isEqualTo("password-reset-" + token.get("id"));
    }

    @Test
    void anUnknownAddressLooksIdenticalAndSendsNothing() throws Exception {
        MvcResult result = forgot("nobody@example.com")
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        verifyNoInteractions(emailClient());
        assertThat(countTokens()).isZero();
    }

    @Test
    void anUnverifiedAccountLooksIdenticalAndSendsNothing() throws Exception {
        register(EMAIL).andExpect(status().isAccepted());
        reset(emailClient());

        MvcResult result = forgot(EMAIL)
            .andExpect(status().isNoContent())
            .andReturn();

        // An account that has never confirmed its address has not proven it owns that
        // inbox, so a reset link would be a way to claim somebody else's address.
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        verifyNoInteractions(emailClient());
        assertThat(countTokens()).isZero();
    }

    @Test
    void theAddressIsNormalisedBeforeItIsUsed() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);
        reset(emailClient());

        forgot("  KENNETH@Example.com  ").andExpect(status().isNoContent());

        assertThat(lastMessage().to()).isEqualTo(EMAIL);
        assertThat(countTokens()).isEqualTo(1);
    }

    @Test
    void aProviderFailureNeverChangesTheResponseOrLeaksProviderDetail() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);
        reset(emailClient());

        doThrow(new EmailProviderException("Failed to send the email through the email provider"))
            .when(emailClient()).send(any(EmailMessage.class));

        MvcResult result = forgot(EMAIL)
            .andExpect(status().isNoContent())
            .andReturn();

        String body = result.getResponse().getContentAsString();

        assertThat(body).isEmpty();
        assertThat(body).doesNotContain("test-resend-api-key");
        assertThat(body).doesNotContain("resend.com");
        assertThat(countTokens()).isEqualTo(1);
    }

    @Test
    void theLimitAppliesPerNormalisedEmailAcrossClientAddresses() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);
        reset(emailClient());

        for (int i = 0; i < RESETS_PER_HOUR; i++) {
            forgot(EMAIL, "10.0.0." + i).andExpect(status().isNoContent());
        }

        // Same address, a client address that has asked for nothing yet: the email's own
        // counter is what is exhausted, so moving to another machine does not help.
        forgot("KENNETH@example.com", "10.0.0.9")
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.message").value(startsWith("Too many requests.")));
    }

    @Test
    void theLimitAppliesPerClientAddressAcrossEmails() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);
        reset(emailClient());

        for (int i = 0; i < RESETS_PER_HOUR; i++) {
            forgot("nobody" + i + "@example.com", "10.0.0.1").andExpect(status().isNoContent());
        }

        forgot("someone.else@example.com", "10.0.0.1")
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        // Another client is unaffected, and so is an address that has not been asked for.
        forgot(EMAIL, "10.0.0.2").andExpect(status().isNoContent());
        assertThat(countTokens()).isEqualTo(1);
    }

    @Test
    void requestingAResetReturnsBadRequestForAnInvalidAddress() throws Exception {
        forgot("not-an-email")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("email: must be a well-formed email address"));

        verifyNoInteractions(emailClient());
    }

    @Test
    void theEmailProviderIsNeverReachedForReal() throws Exception {
        registerVerifiedUser("Kenneth", EMAIL, VALID_PASSWORD, null);
        reset(emailClient());

        forgot(EMAIL).andExpect(status().isNoContent());

        // The provider boundary is replaced for every integration test, so no test can
        // send email or need a real Resend key.
        assertThat(mockingDetails(emailClient()).isMock()).isTrue();
    }

    private ResultActions register(String email) throws Exception {
        return mockMvc.perform(post(REGISTER_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new RegisterRequest("Kenneth", email, VALID_PASSWORD))));
    }

    private ResultActions forgot(String email) throws Exception {
        return forgot(email, "127.0.0.1");
    }

    private ResultActions forgot(String email, String clientIp) throws Exception {
        return mockMvc.perform(post(FORGOT_ENDPOINT)
            .with(fromAddress(clientIp))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }

    private RequestPostProcessor fromAddress(String clientIp) {
        return request -> {
            request.setRemoteAddr(clientIp);

            return request;
        };
    }

    private EmailMessage lastMessage() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);

        verify(emailClient(), atLeastOnce()).send(captor.capture());

        return captor.getValue();
    }

    private Map<String, Object> tokenRow() {
        return jdbcTemplate.queryForMap("SELECT * FROM password_reset_token");
    }

    private int countTokens() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM password_reset_token", Integer.class);
    }

}

package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.auth.dto.GoogleClaims;
import com.synapse.backend.auth.dto.GoogleLoginRequest;
import com.synapse.backend.auth.exceptions.InvalidGoogleCredentialException;
import com.synapse.backend.email.dto.EmailMessage;
import com.synapse.backend.support.PostgresIntegrationTest;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * How a Google account behaves in the flows that were written before it existed.
 *
 * <p>A Google-only account can give itself a password through the ordinary forgotten-password
 * flow, and a Google-linked account can move its Synapse address through the ordinary
 * email-change flow. The two identities stay independent throughout: the subject never
 * changes when the address does, and the address is never rewritten from Google.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class GoogleAccountRecoveryIntegrationTest extends PostgresIntegrationTest {

    private static final String NONCE_ENDPOINT = "/api/auth/google/nonce";
    private static final String GOOGLE_ENDPOINT = "/api/auth/google";
    private static final String LOGIN_ENDPOINT = "/api/auth/login";
    private static final String FORGOT_ENDPOINT = "/api/auth/password/forgot";
    private static final String RESET_ENDPOINT = "/api/auth/password/reset";
    private static final String VERIFY_ENDPOINT = "/api/auth/email/verify";
    private static final String EMAIL_CHANGE_ENDPOINT = "/api/user/email-change";

    private static final String NONCE_COOKIE = "googleNonce";
    private static final String CREDENTIAL = "google-id-token";
    private static final String SUBJECT = "112233445566778899000";
    private static final String GMAIL = "ada@gmail.com";
    private static final String NEW_EMAIL = "ada.new@example.com";
    private static final String NEW_PASSWORD = "new-password123";

    private static final Pattern RESET_LINK = Pattern.compile("reset-password\\?token=([A-Za-z0-9\\-_%]+)");
    private static final Pattern VERIFY_LINK = Pattern.compile("verify-email\\?token=([A-Za-z0-9\\-_%]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private GoogleTokenVerifier googleTokenVerifier;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void aGoogleOnlyAccountCanGiveItselfAPasswordThroughTheResetFlow() throws Exception {
        signInWithGoogle(claims(GMAIL)).andExpect(status().isOk());

        assertThat(passwordHashOf(GMAIL)).isNull();

        resetPassword(requestResetLink(GMAIL), NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(passwordHashOf(GMAIL)).startsWith("$2");
    }

    @Test
    void bothLoginMethodsWorkOnceThePasswordIsSet() throws Exception {
        signInWithGoogle(claims(GMAIL)).andExpect(status().isOk());
        resetPassword(requestResetLink(GMAIL), NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", GMAIL, "password", NEW_PASSWORD))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(GMAIL));

        signInWithGoogle(claims(GMAIL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(GMAIL));

        assertThat(googleSubjectOf(GMAIL)).isEqualTo(SUBJECT);
    }

    @Test
    void aGoogleLinkedAccountCanStillChangeItsSynapseEmail() throws Exception {
        String accessToken = googleAccessToken();

        reset(emailClient());

        mockMvc.perform(post(EMAIL_CHANGE_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", NEW_EMAIL))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.pendingEmail").value(NEW_EMAIL));

        mockMvc.perform(post(VERIFY_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", capturedToken(VERIFY_LINK)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(NEW_EMAIL));

        assertThat(googleSubjectOf(NEW_EMAIL)).isEqualTo(SUBJECT);
        assertThat(countUsers()).isEqualTo(1);
    }

    @Test
    void aLaterGoogleLoginReachesThatAccountAndReturnsTheEditedAddress() throws Exception {
        String accessToken = googleAccessToken();

        reset(emailClient());

        mockMvc.perform(post(EMAIL_CHANGE_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", NEW_EMAIL))))
            .andExpect(status().isAccepted());

        mockMvc.perform(post(VERIFY_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", capturedToken(VERIFY_LINK)))))
            .andExpect(status().isOk());

        signInWithGoogle(claims(GMAIL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(NEW_EMAIL));

        assertThat(countUsers()).isEqualTo(1);
        assertThat(googleSubjectOf(NEW_EMAIL)).isEqualTo(SUBJECT);
    }

    private String googleAccessToken() throws Exception {
        MvcResult result = signInWithGoogle(claims(GMAIL)).andExpect(status().isOk()).andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private ResultActions signInWithGoogle(GoogleClaims claims) throws Exception {
        String nonce = issuedNonce();

        doThrow(new InvalidGoogleCredentialException()).when(googleTokenVerifier).verify(any(), any());
        doReturn(claims).when(googleTokenVerifier).verify(eq(CREDENTIAL), eq(nonce));

        return mockMvc.perform(post(GOOGLE_ENDPOINT)
            .cookie(new Cookie(NONCE_COOKIE, nonce))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new GoogleLoginRequest(CREDENTIAL))));
    }

    private GoogleClaims claims(String email) {
        return new GoogleClaims(SUBJECT, email, true, "Ada Lovelace", null);
    }

    private String issuedNonce() throws Exception {
        MvcResult result = mockMvc.perform(post(NONCE_ENDPOINT))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("nonce").asString();
    }

    private String requestResetLink(String email) throws Exception {
        reset(emailClient());

        mockMvc.perform(post(FORGOT_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))))
            .andExpect(status().isNoContent());

        return capturedToken(RESET_LINK);
    }

    private ResultActions resetPassword(String token, String newPassword) throws Exception {
        return mockMvc.perform(post(RESET_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("token", token, "newPassword", newPassword))));
    }

    private String capturedToken(Pattern linkPattern) {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);

        verify(emailClient(), atLeastOnce()).send(captor.capture());

        Matcher matcher = linkPattern.matcher(captor.getValue().text());

        assertThat(matcher.find()).isTrue();

        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    private String passwordHashOf(String email) {
        return jdbcTemplate.queryForObject("SELECT password_hash FROM app_user WHERE email = ?", String.class, email);
    }

    private String googleSubjectOf(String email) {
        return jdbcTemplate.queryForObject(
            "SELECT google_subject FROM app_user WHERE email = ?",
            String.class,
            email
        );
    }

    private int countUsers() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class);
    }

}

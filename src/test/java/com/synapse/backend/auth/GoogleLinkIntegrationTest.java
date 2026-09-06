package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import com.synapse.backend.auth.dto.ChangePasswordRequest;
import com.synapse.backend.auth.dto.GoogleClaims;
import com.synapse.backend.auth.dto.GoogleLoginRequest;
import com.synapse.backend.auth.dto.LinkGoogleRequest;
import com.synapse.backend.auth.dto.LoginRequest;
import com.synapse.backend.auth.dto.UnlinkGoogleRequest;
import com.synapse.backend.auth.exceptions.InvalidGoogleCredentialException;
import com.synapse.backend.support.PostgresIntegrationTest;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * The authenticated half of Google sign-in: attaching a Google Account whose address is not
 * the Synapse one, and detaching it again. Both sides prove themselves here, which is why
 * the two addresses are allowed to differ and why neither is copied onto the other.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GoogleLinkIntegrationTest extends PostgresIntegrationTest {

    private static final String NONCE_ENDPOINT = "/api/auth/google/nonce";
    private static final String GOOGLE_ENDPOINT = "/api/auth/google";
    private static final String LINK_ENDPOINT = "/api/user/google-link";
    private static final String DETAILS_ENDPOINT = "/api/user/details";
    private static final String LOGIN_ENDPOINT = "/api/auth/login";
    private static final String REFRESH_ENDPOINT = "/api/auth/refresh";
    private static final String REFRESH_COOKIE = "refreshToken";
    private static final String CHANGE_PASSWORD_ENDPOINT = "/api/auth/password";

    private static final String NONCE_COOKIE = "googleNonce";
    private static final String CREDENTIAL = "google-id-token";
    private static final String SUBJECT = "112233445566778899000";
    private static final String OTHER_SUBJECT = "998877665544332211000";
    private static final String SYNAPSE_EMAIL = "ada@work.example";
    private static final String GOOGLE_EMAIL = "ada.personal@gmail.com";
    private static final String VALID_PASSWORD = "password123";

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
    void linkingAttachesAGoogleAccountWhoseAddressIsNotTheSynapseOne() throws Exception {
        String accessToken = registerAndAuthenticate("Ada Lovelace", SYNAPSE_EMAIL, VALID_PASSWORD);

        link(accessToken, googleClaims(SUBJECT, GOOGLE_EMAIL), VALID_PASSWORD)
            .andExpect(status().isNoContent());

        assertThat(googleSubjectOf(SYNAPSE_EMAIL)).isEqualTo(SUBJECT);
        assertThat(countUsers()).isEqualTo(1);
    }

    @Test
    void userDetailsReportWhichWaysInTheAccountHas() throws Exception {
        String accessToken = registerAndAuthenticate("Ada Lovelace", SYNAPSE_EMAIL, VALID_PASSWORD);

        mockMvc.perform(get(DETAILS_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasPassword").value(true))
            .andExpect(jsonPath("$.googleLinked").value(false));

        link(accessToken, googleClaims(SUBJECT, GOOGLE_EMAIL), VALID_PASSWORD)
            .andExpect(status().isNoContent());

        mockMvc.perform(get(DETAILS_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasPassword").value(true))
            .andExpect(jsonPath("$.googleLinked").value(true));
    }

    @Test
    void aLinkedAccountCanThenContinueWithGoogleAndKeepsItsPassword() throws Exception {
        String accessToken = registerAndAuthenticate("Ada Lovelace", SYNAPSE_EMAIL, VALID_PASSWORD);

        link(accessToken, googleClaims(SUBJECT, GOOGLE_EMAIL), VALID_PASSWORD)
            .andExpect(status().isNoContent());

        signInWithGoogle(googleClaims(SUBJECT, GOOGLE_EMAIL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(SYNAPSE_EMAIL))
            .andExpect(jsonPath("$.fullName").value("Ada Lovelace"));

        mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(SYNAPSE_EMAIL, VALID_PASSWORD))))
            .andExpect(status().isOk());

        assertThat(countUsers()).isEqualTo(1);
    }

    @Test
    void linkingRequiresTheCurrentPassword() throws Exception {
        String accessToken = registerAndAuthenticate("Ada Lovelace", SYNAPSE_EMAIL, VALID_PASSWORD);

        link(accessToken, googleClaims(SUBJECT, GOOGLE_EMAIL), "wrong-password")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Current password is incorrect."));

        assertThat(googleSubjectOf(SYNAPSE_EMAIL)).isNull();
    }

    @Test
    void linkingRequiresTheNonceCookie() throws Exception {
        String accessToken = registerAndAuthenticate("Ada Lovelace", SYNAPSE_EMAIL, VALID_PASSWORD);
        LinkGoogleRequest request = new LinkGoogleRequest(CREDENTIAL, VALID_PASSWORD);

        mockMvc.perform(post(LINK_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Google sign-in could not be verified. Try again."));

        assertThat(googleSubjectOf(SYNAPSE_EMAIL)).isNull();
    }

    @Test
    void linkingRequiresAuthentication() throws Exception {
        mockMvc.perform(post(LINK_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LinkGoogleRequest(CREDENTIAL, VALID_PASSWORD))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void linkingIsRejectedWhenThatGoogleAccountBelongsToSomebodyElse() throws Exception {
        signInWithGoogle(googleClaims(SUBJECT, "someone@gmail.com")).andExpect(status().isOk());

        String accessToken = registerAndAuthenticate("Ada Lovelace", SYNAPSE_EMAIL, VALID_PASSWORD);

        link(accessToken, googleClaims(SUBJECT, "someone@gmail.com"), VALID_PASSWORD)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message")
                .value("That Google Account is already linked to another account."));

        assertThat(googleSubjectOf(SYNAPSE_EMAIL)).isNull();
    }

    @Test
    void linkingIsRejectedWhenTheAccountAlreadyHasADifferentGoogleAccount() throws Exception {
        String accessToken = registerAndAuthenticate("Ada Lovelace", SYNAPSE_EMAIL, VALID_PASSWORD);

        link(accessToken, googleClaims(SUBJECT, GOOGLE_EMAIL), VALID_PASSWORD)
            .andExpect(status().isNoContent());

        link(accessToken, googleClaims(OTHER_SUBJECT, "other@gmail.com"), VALID_PASSWORD)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message")
                .value("This account is already linked to a different Google Account. Unlink that one first."));

        assertThat(googleSubjectOf(SYNAPSE_EMAIL)).isEqualTo(SUBJECT);
    }

    @Test
    void linkingTheGoogleAccountThatIsAlreadyLinkedChangesNothing() throws Exception {
        String accessToken = registerAndAuthenticate("Ada Lovelace", SYNAPSE_EMAIL, VALID_PASSWORD);

        link(accessToken, googleClaims(SUBJECT, GOOGLE_EMAIL), VALID_PASSWORD)
            .andExpect(status().isNoContent());

        link(accessToken, googleClaims(SUBJECT, GOOGLE_EMAIL), VALID_PASSWORD)
            .andExpect(status().isNoContent());

        assertThat(googleSubjectOf(SYNAPSE_EMAIL)).isEqualTo(SUBJECT);
    }

    @Test
    void unlinkingRemovesTheGoogleIdentityAndLeavesTheAccountAlone() throws Exception {
        String accessToken = registerAndAuthenticate("Ada Lovelace", SYNAPSE_EMAIL, VALID_PASSWORD);

        link(accessToken, googleClaims(SUBJECT, GOOGLE_EMAIL), VALID_PASSWORD)
            .andExpect(status().isNoContent());

        unlink(accessToken, VALID_PASSWORD).andExpect(status().isNoContent());

        assertThat(googleSubjectOf(SYNAPSE_EMAIL)).isNull();

        mockMvc.perform(get(DETAILS_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
            .andExpect(jsonPath("$.email").value(SYNAPSE_EMAIL))
            .andExpect(jsonPath("$.googleLinked").value(false));

        mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(SYNAPSE_EMAIL, VALID_PASSWORD))))
            .andExpect(status().isOk());
    }

    /**
     * Unlinking is a change to the ways into the account, so it ends every session, exactly as
     * a password change does. A session somebody obtained through a Google Account that has
     * since been compromised must not stay refreshable for thirty days after its owner cut the
     * link — that is the situation somebody unlinking in a hurry is trying to end.
     */
    @Test
    void unlinkingRevokesEverySessionAndClearsTheCookie() throws Exception {
        String accessToken = registerAndAuthenticate("Ada Lovelace", SYNAPSE_EMAIL, VALID_PASSWORD);

        link(accessToken, googleClaims(SUBJECT, GOOGLE_EMAIL), VALID_PASSWORD)
            .andExpect(status().isNoContent());

        Cookie otherSession = loginAndReadRefreshCookie();

        assertThat(activeRefreshTokens()).isGreaterThanOrEqualTo(2);

        MvcResult result = unlink(accessToken, VALID_PASSWORD)
            .andExpect(status().isNoContent())
            .andReturn();

        Cookie cleared = result.getResponse().getCookie(REFRESH_COOKIE);

        assertThat(cleared).isNotNull();
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(activeRefreshTokens()).isZero();

        mockMvc.perform(post(REFRESH_ENDPOINT).cookie(otherSession))
            .andExpect(status().isUnauthorized());
    }

    /**
     * Unlinking nothing removes nothing, so it must end nothing. A retried or duplicated
     * request would otherwise sign somebody out of every device for no reason at all.
     */
    @Test
    void unlinkingWhenNothingIsLinkedLeavesTheSessionAlone() throws Exception {
        String accessToken = registerAndAuthenticate("Ada Lovelace", SYNAPSE_EMAIL, VALID_PASSWORD);
        Cookie session = loginAndReadRefreshCookie();

        int before = activeRefreshTokens();

        MvcResult result = unlink(accessToken, VALID_PASSWORD)
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(result.getResponse().getCookie(REFRESH_COOKIE)).isNull();
        assertThat(activeRefreshTokens()).isEqualTo(before);

        mockMvc.perform(post(REFRESH_ENDPOINT).cookie(session))
            .andExpect(status().isOk());
    }

    /** The second of two unlinks removes nothing, so it must not end the session it was given. */
    @Test
    void aRepeatedUnlinkDoesNotEndTheNewSession() throws Exception {
        String accessToken = registerAndAuthenticate("Ada Lovelace", SYNAPSE_EMAIL, VALID_PASSWORD);

        link(accessToken, googleClaims(SUBJECT, GOOGLE_EMAIL), VALID_PASSWORD)
            .andExpect(status().isNoContent());

        unlink(accessToken, VALID_PASSWORD).andExpect(status().isNoContent());

        Cookie session = loginAndReadRefreshCookie();

        MvcResult result = unlink(accessToken, VALID_PASSWORD)
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(result.getResponse().getCookie(REFRESH_COOKIE)).isNull();

        mockMvc.perform(post(REFRESH_ENDPOINT).cookie(session))
            .andExpect(status().isOk());
    }

    @Test
    void unlinkingRequiresTheCurrentPassword() throws Exception {
        String accessToken = registerAndAuthenticate("Ada Lovelace", SYNAPSE_EMAIL, VALID_PASSWORD);

        link(accessToken, googleClaims(SUBJECT, GOOGLE_EMAIL), VALID_PASSWORD)
            .andExpect(status().isNoContent());

        unlink(accessToken, "wrong-password")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Current password is incorrect."));

        assertThat(googleSubjectOf(SYNAPSE_EMAIL)).isEqualTo(SUBJECT);
    }

    @Test
    void aGoogleOnlyAccountCannotUnlinkItsOnlyWayIn() throws Exception {
        String accessToken = accessTokenOf(
            signInWithGoogle(googleClaims(SUBJECT, GOOGLE_EMAIL)).andExpect(status().isOk()).andReturn()
        );

        unlink(accessToken, VALID_PASSWORD)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message")
                .value("This account signs in with Google only. Set a password before unlinking Google."));

        assertThat(googleSubjectOf(GOOGLE_EMAIL)).isEqualTo(SUBJECT);
    }

    @Test
    void aGoogleOnlyAccountCannotChangeAPasswordItDoesNotHave() throws Exception {
        String accessToken = accessTokenOf(
            signInWithGoogle(googleClaims(SUBJECT, GOOGLE_EMAIL)).andExpect(status().isOk()).andReturn()
        );

        ChangePasswordRequest request = new ChangePasswordRequest(VALID_PASSWORD, "new-password123");

        mockMvc.perform(put(CHANGE_PASSWORD_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message")
                .value("This account has no password. Use the forgotten-password flow to set one."));

        assertThat(passwordHashOf(GOOGLE_EMAIL)).isNull();
    }

    private ResultActions link(String accessToken, GoogleClaims claims, String password) throws Exception {
        String nonce = issuedNonce();

        stubGoogle(nonce, claims);

        return mockMvc.perform(post(LINK_ENDPOINT)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .cookie(new Cookie(NONCE_COOKIE, nonce))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new LinkGoogleRequest(CREDENTIAL, password))));
    }

    private ResultActions unlink(String accessToken, String password) throws Exception {
        return mockMvc.perform(delete(LINK_ENDPOINT)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UnlinkGoogleRequest(password))));
    }

    private ResultActions signInWithGoogle(GoogleClaims claims) throws Exception {
        String nonce = issuedNonce();

        stubGoogle(nonce, claims);

        return mockMvc.perform(post(GOOGLE_ENDPOINT)
            .cookie(new Cookie(NONCE_COOKIE, nonce))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new GoogleLoginRequest(CREDENTIAL))));
    }

    private void stubGoogle(String tokenNonce, GoogleClaims claims) {
        doThrow(new InvalidGoogleCredentialException()).when(googleTokenVerifier).verify(any(), any());
        doReturn(claims).when(googleTokenVerifier).verify(eq(CREDENTIAL), eq(tokenNonce));
    }

    private GoogleClaims googleClaims(String subject, String email) {
        return new GoogleClaims(subject, email, true, "Ada Lovelace", null);
    }

    private String issuedNonce() throws Exception {
        MvcResult result = mockMvc.perform(post(NONCE_ENDPOINT))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("nonce").asString();
    }

    private String accessTokenOf(MvcResult result) throws Exception {
        return objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asString();
    }

    private Cookie loginAndReadRefreshCookie() throws Exception {
        return mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(SYNAPSE_EMAIL, VALID_PASSWORD))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie(REFRESH_COOKIE);
    }

    private int activeRefreshTokens() {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM refresh_token t
            JOIN app_user u ON u.id = t.user_id
            WHERE u.email = ? AND t.revoked_at IS NULL
            """,
            Integer.class,
            SYNAPSE_EMAIL
        );
    }

    private String googleSubjectOf(String email) {
        return jdbcTemplate.queryForObject(
            "SELECT google_subject FROM app_user WHERE email = ?",
            String.class,
            email
        );
    }

    private String passwordHashOf(String email) {
        return jdbcTemplate.queryForObject("SELECT password_hash FROM app_user WHERE email = ?", String.class, email);
    }

    private int countUsers() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class);
    }

}

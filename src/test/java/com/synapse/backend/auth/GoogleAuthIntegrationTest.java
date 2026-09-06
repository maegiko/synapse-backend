package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.auth.dto.GoogleClaims;
import com.synapse.backend.auth.dto.GoogleLoginRequest;
import com.synapse.backend.auth.dto.LoginRequest;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.auth.exceptions.InvalidGoogleCredentialException;
import com.synapse.backend.email.dto.EmailMessage;
import com.synapse.backend.support.PostgresIntegrationTest;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * "Continue with Google" end to end: one endpoint that decides for itself whether a verified
 * credential is a new account, a link to an existing one, or a returning sign-in, and answers
 * every one of them with the ordinary Synapse session.
 *
 * <p>The verifier is the mocked boundary, so nothing here reaches Google. What it returns is
 * by definition already verified, which is exactly the contract
 * {@code GoogleTokenVerifier} has with the rest of the application.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class GoogleAuthIntegrationTest extends PostgresIntegrationTest {

    private static final String NONCE_ENDPOINT = "/api/auth/google/nonce";
    private static final String GOOGLE_ENDPOINT = "/api/auth/google";
    private static final String LOGIN_ENDPOINT = "/api/auth/login";
    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String VERIFY_ENDPOINT = "/api/auth/email/verify";

    private static final String NONCE_COOKIE = "googleNonce";
    private static final String REFRESH_COOKIE = "refreshToken";

    private static final String CREDENTIAL = "google-id-token";
    private static final String SUBJECT = "112233445566778899000";
    private static final String OTHER_SUBJECT = "998877665544332211000";
    private static final String GMAIL = "ada@gmail.com";
    private static final String VALID_PASSWORD = "password123";

    private static final Pattern LINK_PATTERN = Pattern.compile("verify-email\\?token=([A-Za-z0-9\\-_%]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private GoogleTokenVerifier googleTokenVerifier;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void nonceEndpointIssuesANonceAndBindsItToTheBrowser() throws Exception {
        MvcResult result = mockMvc.perform(post(NONCE_ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nonce").isNotEmpty())
            .andReturn();

        Cookie cookie = result.getResponse().getCookie(NONCE_COOKIE);
        String nonce = objectMapper.readTree(result.getResponse().getContentAsString()).get("nonce").asString();

        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo(nonce);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api");
        assertThat(cookie.getDomain()).isNull();
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("SameSite=None");
    }

    @Test
    void continueWithGoogleCreatesAVerifiedPasswordlessAccount() throws Exception {
        signInWithGoogle(gmailClaims("Ada Lovelace"), "Australia/Sydney")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
            .andExpect(jsonPath("$.email").value(GMAIL))
            .andExpect(jsonPath("$.accessToken").isNotEmpty());

        Map<String, Object> saved = userRow(GMAIL);

        assertThat(saved.get("google_subject")).isEqualTo(SUBJECT);
        assertThat(saved.get("password_hash")).isNull();
        assertThat(saved.get("email_verified_at")).isNotNull();
        assertThat(saved.get("time_zone")).isEqualTo("Australia/Sydney");
        assertThat(saved.get("full_name")).isEqualTo("Ada Lovelace");
    }

    @Test
    void aNewGoogleAccountIsNeverSentAVerificationEmail() throws Exception {
        signInWithGoogle(gmailClaims("Ada Lovelace"), null).andExpect(status().isOk());

        assertThat(countVerificationTokens(GMAIL)).isZero();
        verifyNoInteractions(emailClient());
    }

    @Test
    void aNewGoogleAccountReceivesTheOrdinaryAccessAndRefreshSession() throws Exception {
        MvcResult result = signInWithGoogle(gmailClaims("Ada Lovelace"), null)
            .andExpect(status().isOk())
            .andReturn();

        long userId = userIdOf(GMAIL);
        String accessToken = accessTokenOf(result);
        Cookie refreshCookie = result.getResponse().getCookie(REFRESH_COOKIE);

        assertThat(jwtDecoder.decode(accessToken).getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(jwtDecoder.decode(accessToken).getClaimAsString("email")).isEqualTo(GMAIL);
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isNotEmpty();
        assertThat(refreshCookie.getPath()).isEqualTo("/api/auth");
        assertThat(countRefreshTokens(userId)).isEqualTo(1);
    }

    @Test
    void aGoogleAccountIsNamedFromTheValidatedGoogleName() throws Exception {
        signInWithGoogle(gmailClaims("  ada   LOVELACE  "), null).andExpect(status().isOk());

        assertThat(userRow(GMAIL).get("full_name")).isEqualTo("Ada Lovelace");
    }

    @Test
    void aGoogleAccountFallsBackToUtcWhenNoTimeZoneIsSupplied() throws Exception {
        signInWithGoogle(gmailClaims("Ada Lovelace"), null).andExpect(status().isOk());

        assertThat(userRow(GMAIL).get("time_zone")).isEqualTo("UTC");
    }

    @Test
    void continueWithGoogleRejectsATimeZoneThatIsNotARealZone() throws Exception {
        signInWithGoogle(gmailClaims("Ada Lovelace"), "Mars/Olympus_Mons")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("timeZone: must be a valid IANA time zone"));

        assertThat(countUsers(GMAIL)).isZero();
    }

    @Test
    void aReturningGoogleUserSignsInToTheSameAccount() throws Exception {
        signInWithGoogle(gmailClaims("Ada Lovelace"), null).andExpect(status().isOk());

        long firstId = userIdOf(GMAIL);

        signInWithGoogle(gmailClaims("Ada Lovelace"), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(GMAIL));

        assertThat(userIdOf(GMAIL)).isEqualTo(firstId);
        assertThat(countAllUsers()).isEqualTo(1);
    }

    @Test
    void theSubjectWinsWhenGoogleAndSynapseEmailsHaveDrifted() throws Exception {
        signInWithGoogle(gmailClaims("Ada Lovelace"), null).andExpect(status().isOk());

        long userId = userIdOf(GMAIL);

        jdbcTemplate.update("UPDATE app_user SET email = ? WHERE id = ?", "ada@synapse.example", userId);

        signInWithGoogle(new GoogleClaims(SUBJECT, GMAIL, true, "Ada Lovelace", null), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("ada@synapse.example"));

        assertThat(countAllUsers()).isEqualTo(1);
        assertThat(userRow("ada@synapse.example").get("google_subject")).isEqualTo(SUBJECT);
    }

    @Test
    void aGoogleLoginNeverWritesTheNameOrEmailBackFromGoogle() throws Exception {
        signInWithGoogle(gmailClaims("Ada Lovelace"), null).andExpect(status().isOk());

        long userId = userIdOf(GMAIL);

        jdbcTemplate.update("UPDATE app_user SET full_name = ? WHERE id = ?", "Ada L", userId);

        signInWithGoogle(new GoogleClaims(SUBJECT, "renamed@gmail.com", true, "Renamed Person", null), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Ada L"))
            .andExpect(jsonPath("$.email").value(GMAIL));

        Map<String, Object> saved = userRow(GMAIL);

        assertThat(saved.get("full_name")).isEqualTo("Ada L");
        assertThat(saved.get("id")).isEqualTo(userId);
    }

    @Test
    void anExactEmailMatchLinksToAVerifiedLocalAccount() throws Exception {
        registerVerifiedUser("Ada Lovelace", GMAIL, VALID_PASSWORD, "Europe/London");

        long userId = userIdOf(GMAIL);
        String passwordHash = (String) userRow(GMAIL).get("password_hash");

        signInWithGoogle(gmailClaims("Ada From Google"), "Australia/Sydney")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Ada Lovelace"));

        Map<String, Object> saved = userRow(GMAIL);

        assertThat(saved.get("id")).isEqualTo(userId);
        assertThat(saved.get("google_subject")).isEqualTo(SUBJECT);
        assertThat(saved.get("password_hash")).isEqualTo(passwordHash);
        assertThat(saved.get("full_name")).isEqualTo("Ada Lovelace");
        assertThat(saved.get("time_zone")).isEqualTo("Europe/London");
        assertThat(countAllUsers()).isEqualTo(1);
    }

    @Test
    void anAutomaticallyLinkedAccountCanUseEitherLoginMethod() throws Exception {
        registerVerifiedUser("Ada Lovelace", GMAIL, VALID_PASSWORD, null);

        signInWithGoogle(gmailClaims("Ada Lovelace"), null).andExpect(status().isOk());

        mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(GMAIL, VALID_PASSWORD))))
            .andExpect(status().isOk());

        signInWithGoogle(gmailClaims("Ada Lovelace"), null).andExpect(status().isOk());
    }

    @Test
    void claimingAnUnverifiedAccountVerifiesItAndClearsItsPassword() throws Exception {
        registerUnverified("Impostor", GMAIL, "attacker-password");

        long userId = userIdOf(GMAIL);

        signInWithGoogle(gmailClaims("Ada Lovelace"), "Australia/Sydney")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Ada Lovelace"));

        Map<String, Object> saved = userRow(GMAIL);

        assertThat(saved.get("id")).isEqualTo(userId);
        assertThat(saved.get("google_subject")).isEqualTo(SUBJECT);
        assertThat(saved.get("password_hash")).isNull();
        assertThat(saved.get("email_verified_at")).isNotNull();
        assertThat(saved.get("full_name")).isEqualTo("Ada Lovelace");
        assertThat(saved.get("time_zone")).isEqualTo("Australia/Sydney");
        assertThat(countAllUsers()).isEqualTo(1);
    }

    @Test
    void aPreregisteringAttackersPasswordCannotBeUsedAfterGoogleClaimsTheAccount() throws Exception {
        registerUnverified("Impostor", GMAIL, "attacker-password");

        signInWithGoogle(gmailClaims("Ada Lovelace"), null).andExpect(status().isOk());

        mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(GMAIL, "attacker-password"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void claimingAnUnverifiedAccountInvalidatesItsOutstandingRegistrationLink() throws Exception {
        registerUnverified("Impostor", GMAIL, "attacker-password");

        String registrationToken = capturedRegistrationToken();

        signInWithGoogle(gmailClaims("Ada Lovelace"), null).andExpect(status().isOk());

        assertThat(activeVerificationTokens(GMAIL)).isZero();

        mockMvc.perform(post(VERIFY_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + registrationToken + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void anEmailAlreadyLinkedToAnotherGoogleAccountIsNotMerged() throws Exception {
        signInWithGoogle(gmailClaims("Ada Lovelace"), null).andExpect(status().isOk());

        long userId = userIdOf(GMAIL);

        signInWithGoogle(new GoogleClaims(OTHER_SUBJECT, GMAIL, true, "Someone Else", null), null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message")
                .value("That email address is already linked to a different Google Account."));

        Map<String, Object> saved = userRow(GMAIL);

        assertThat(saved.get("id")).isEqualTo(userId);
        assertThat(saved.get("google_subject")).isEqualTo(SUBJECT);
        assertThat(countAllUsers()).isEqualTo(1);
    }

    @Test
    void aThirdPartyGoogleAddressCannotCreateAnAccount() throws Exception {
        signInWithGoogle(new GoogleClaims(SUBJECT, "ada@outlook.com", true, "Ada Lovelace", null), null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                "Google does not verify ownership of this email address. Register with Synapse, confirm your "
                    + "address, then link Google from your account settings."));

        assertThat(countAllUsers()).isZero();
    }

    @Test
    void aThirdPartyGoogleAddressCannotClaimAnExistingAccountEither() throws Exception {
        registerVerifiedUser("Ada Lovelace", "ada@outlook.com", VALID_PASSWORD, null);

        signInWithGoogle(new GoogleClaims(SUBJECT, "ada@outlook.com", true, "Ada Lovelace", null), null)
            .andExpect(status().isBadRequest());

        assertThat(userRow("ada@outlook.com").get("google_subject")).isNull();
    }

    @Test
    void aWorkspaceAddressWithAHostedDomainCreatesAnAccount() throws Exception {
        GoogleClaims claims = new GoogleClaims(SUBJECT, "ada@synapse.school", true, "Ada Lovelace", "synapse.school");

        signInWithGoogle(claims, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("ada@synapse.school"));

        assertThat(userRow("ada@synapse.school").get("google_subject")).isEqualTo(SUBJECT);
    }

    @Test
    void aGoogleAddressThatGoogleHasNotVerifiedIsRejected() throws Exception {
        signInWithGoogle(new GoogleClaims(SUBJECT, GMAIL, false, "Ada Lovelace", null), null)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Google sign-in could not be verified. Try again."));

        assertThat(countAllUsers()).isZero();
    }

    @Test
    void aGoogleOnlyAccountFailsPasswordLoginWithTheGenericMessage() throws Exception {
        signInWithGoogle(gmailClaims("Ada Lovelace"), null).andExpect(status().isOk());

        mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(GMAIL, VALID_PASSWORD))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void continueWithGoogleRejectsAMissingCredential() throws Exception {
        String nonce = issuedNonce();

        mockMvc.perform(post(GOOGLE_ENDPOINT)
                .cookie(new Cookie(NONCE_COOKIE, nonce))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"timeZone\": \"UTC\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("credential: must not be blank"));
    }

    /** Issues a nonce, stubs the verifier as if Google minted a token for it, and posts the credential. */
    private ResultActions signInWithGoogle(GoogleClaims claims, String timeZone) throws Exception {
        String nonce = issuedNonce();

        stubGoogle(nonce, claims);

        return mockMvc.perform(post(GOOGLE_ENDPOINT)
            .cookie(new Cookie(NONCE_COOKIE, nonce))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new GoogleLoginRequest(CREDENTIAL, timeZone))));
    }

    /**
     * Makes the mocked verifier behave like the real one: it returns claims only for the
     * nonce the token was minted with, and refuses everything else.
     */
    private void stubGoogle(String tokenNonce, GoogleClaims claims) {
        doThrow(new InvalidGoogleCredentialException()).when(googleTokenVerifier).verify(any(), any());
        doReturn(claims).when(googleTokenVerifier).verify(eq(CREDENTIAL), eq(tokenNonce));
    }

    private String issuedNonce() throws Exception {
        MvcResult result = mockMvc.perform(post(NONCE_ENDPOINT))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("nonce").asString();
    }

    private GoogleClaims gmailClaims(String name) {
        return new GoogleClaims(SUBJECT, GMAIL, true, name, null);
    }

    private void registerUnverified(String fullName, String email, String password) throws Exception {
        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(fullName, email, password))))
            .andExpect(status().isAccepted());
    }

    /** Recovers the raw registration token from the link the mocked provider was handed. */
    private String capturedRegistrationToken() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);

        verify(emailClient()).send(captor.capture());

        Matcher matcher = LINK_PATTERN.matcher(captor.getValue().text());

        assertThat(matcher.find()).isTrue();

        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    private String accessTokenOf(MvcResult result) throws Exception {
        return objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asString();
    }

    private Map<String, Object> userRow(String email) {
        return jdbcTemplate.queryForMap(
            "SELECT id, full_name, email, password_hash, google_subject, time_zone, email_verified_at "
                + "FROM app_user WHERE email = ?",
            email
        );
    }

    private long userIdOf(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, email);
    }

    private int countUsers(String email) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user WHERE email = ?", Integer.class, email);
    }

    private int countAllUsers() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class);
    }

    private int countRefreshTokens(long userId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_token WHERE user_id = ?",
            Integer.class,
            userId
        );
    }

    private int countVerificationTokens(String email) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM email_verification_token WHERE email = ?",
            Integer.class,
            email
        );
    }

    private int activeVerificationTokens(String email) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM email_verification_token "
                + "WHERE email = ? AND consumed_at IS NULL AND invalidated_at IS NULL",
            Integer.class,
            email
        );
    }

}

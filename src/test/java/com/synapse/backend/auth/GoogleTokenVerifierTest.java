package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.synapse.backend.auth.dto.GoogleClaims;
import com.synapse.backend.auth.exceptions.InvalidGoogleCredentialException;

/**
 * Both halves of the verifier, without ever contacting Google.
 *
 * <p>The first half is what a real token is judged on before Google's keys are needed — the
 * issuer, the audience and the expiry — each of which fails a token on its own.</p>
 *
 * <p>The second half is everything the verifier decides after the signature passes: the
 * nonce, the subject, the address, and which claims come out the other side. A real signature
 * cannot be produced here, so those tests supply a verifier that answers the signature
 * question directly. That is the whole reason the package-private constructor exists.</p>
 */
class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "test-google-client-id.apps.googleusercontent.com";
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String SUBJECT = "112233445566778899000";
    private static final String EMAIL = "ada@gmail.com";
    private static final GsonFactory JSON_FACTORY = new GsonFactory();

    private final GoogleTokenVerifier verifier = new GoogleTokenVerifier(
        new GoogleAuthProperties(List.of(CLIENT_ID), Duration.ofMinutes(5))
    );

    @Test
    void aCredentialThatIsNotATokenAtAllIsRejected() {
        assertThatThrownBy(() -> verifier.verify("not-a-token", "nonce"))
            .isInstanceOf(InvalidGoogleCredentialException.class);
    }

    @Test
    void anEmptyCredentialIsRejected() {
        assertThatThrownBy(() -> verifier.verify("", "nonce"))
            .isInstanceOf(InvalidGoogleCredentialException.class);
    }

    @Test
    void aTokenFromAnotherIssuerIsRejected() {
        String token = idToken("https://accounts.example.com", CLIENT_ID, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> verifier.verify(token, "nonce"))
            .isInstanceOf(InvalidGoogleCredentialException.class);
    }

    @Test
    void aTokenAddressedToAnotherClientIsRejected() {
        String token = idToken(GOOGLE_ISSUER, "someone-elses-client-id", Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> verifier.verify(token, "nonce"))
            .isInstanceOf(InvalidGoogleCredentialException.class);
    }

    @Test
    void anExpiredTokenIsRejected() {
        String token = idToken(GOOGLE_ISSUER, CLIENT_ID, Instant.now().minusSeconds(3600));

        assertThatThrownBy(() -> verifier.verify(token, "nonce"))
            .isInstanceOf(InvalidGoogleCredentialException.class);
    }

    /**
     * An unconfigured {@code GOOGLE_CLIENT_ID} must refuse every credential rather than
     * accept any audience. Google's verifier skips the audience check entirely when its
     * audience is null, so the empty list matters.
     */
    @Test
    void anUnconfiguredClientIdRefusesEveryToken() {
        GoogleTokenVerifier unconfigured = new GoogleTokenVerifier(
            new GoogleAuthProperties(List.of(), Duration.ofMinutes(5))
        );
        String token = idToken(GOOGLE_ISSUER, CLIENT_ID, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> unconfigured.verify(token, "nonce"))
            .isInstanceOf(InvalidGoogleCredentialException.class);
    }

    /**
     * Malformed JSON inside otherwise well-formed segments is the case that used to be
     * reported as a Google outage: the library raises {@code IOException} while parsing, the
     * same type it raises when it cannot fetch signing keys. A credential nobody could parse
     * is the caller's problem, not Google's.
     */
    @Test
    void aTokenWhoseJsonIsMalformedIsRejectedRatherThanBlamedOnGoogle() {
        String token = base64Url("{\"alg\":\"RS256\"}")
            + "." + base64Url("{not valid json")
            + "." + base64Url("signature");

        assertThatThrownBy(() -> verifier.verify(token, "nonce"))
            .isInstanceOf(InvalidGoogleCredentialException.class);
    }

    @Test
    void aTokenWhoseSignatureDoesNotCheckOutIsRejected() {
        assertThatThrownBy(() -> signedVerifier(false).verify(signedToken("nonce"), "nonce"))
            .isInstanceOf(InvalidGoogleCredentialException.class);
    }

    @Test
    void aTokenCarryingAnotherAttemptsNonceIsRejected() {
        assertThatThrownBy(() -> signedVerifier(true).verify(signedToken("a-different-nonce"), "nonce"))
            .isInstanceOf(InvalidGoogleCredentialException.class);
    }

    @Test
    void aTokenWithNoSubjectIsRejected() {
        String token = signedToken(payload(p -> p.setSubject(null)));

        assertThatThrownBy(() -> signedVerifier(true).verify(token, "nonce"))
            .isInstanceOf(InvalidGoogleCredentialException.class);
    }

    @Test
    void aTokenWithABlankSubjectIsRejected() {
        String token = signedToken(payload(p -> p.setSubject("   ")));

        assertThatThrownBy(() -> signedVerifier(true).verify(token, "nonce"))
            .isInstanceOf(InvalidGoogleCredentialException.class);
    }

    @Test
    void aTokenWithNoEmailIsRejected() {
        String token = signedToken(payload(p -> p.setEmail(null)));

        assertThatThrownBy(() -> signedVerifier(true).verify(token, "nonce"))
            .isInstanceOf(InvalidGoogleCredentialException.class);
    }

    @Test
    void aGoodTokenYieldsTheClaimsTheRestOfTheApplicationTrusts() {
        GoogleClaims claims = signedVerifier(true).verify(signedToken("nonce"), "nonce");

        assertThat(claims.subject()).isEqualTo(SUBJECT);
        assertThat(claims.email()).isEqualTo(EMAIL);
        assertThat(claims.emailVerified()).isTrue();
        assertThat(claims.name()).isEqualTo("Ada Lovelace");
        assertThat(claims.hostedDomain()).isNull();
    }

    @Test
    void anUnverifiedAddressIsReportedRatherThanRejectedHere() {
        String token = signedToken(payload(p -> p.setEmailVerified(false)));
        GoogleClaims claims = signedVerifier(true).verify(token, "nonce");

        assertThat(claims.emailVerified()).isFalse();
    }

    @Test
    void aWorkspaceTokenCarriesItsHostedDomain() {
        String token = signedToken(payload(p -> p.setHostedDomain("synapse.school")));
        GoogleClaims claims = signedVerifier(true).verify(token, "nonce");

        assertThat(claims.hostedDomain()).isEqualTo("synapse.school");
    }

    /**
     * A verifier whose signature check is decided by the test rather than by Google. Only the
     * signature is stubbed: the token still has to parse, and every claim check below it runs
     * exactly as it does in production.
     */
    private GoogleTokenVerifier signedVerifier(boolean signatureValid) {
        GoogleIdTokenVerifier stub = new GoogleIdTokenVerifier.Builder(
            new NetHttpTransport(),
            JSON_FACTORY
        ) {
            @Override
            public GoogleIdTokenVerifier build() {
                return new GoogleIdTokenVerifier(this) {
                    @Override
                    public boolean verify(GoogleIdToken idToken) {
                        return signatureValid;
                    }
                };
            }
        }.setAudience(List.of(CLIENT_ID)).build();

        return new GoogleTokenVerifier(stub);
    }

    private GoogleIdToken.Payload payload(Consumer<GoogleIdToken.Payload> customise) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();

        payload.setIssuer(GOOGLE_ISSUER);
        payload.setAudience(CLIENT_ID);
        payload.setSubject(SUBJECT);
        payload.setEmail(EMAIL);
        payload.setEmailVerified(true);
        payload.setNonce("nonce");
        payload.set("name", "Ada Lovelace");
        payload.setExpirationTimeSeconds(Instant.now().getEpochSecond() + 600);
        payload.setIssuedAtTimeSeconds(Instant.now().getEpochSecond());

        customise.accept(payload);

        return payload;
    }

    private String signedToken(String nonce) {
        return signedToken(payload(p -> p.setNonce(nonce)));
    }

    /** Serialised the way Google serialises one, with a placeholder for the signature. */
    private String signedToken(GoogleIdToken.Payload payload) {
        JsonWebSignature.Header header = new JsonWebSignature.Header();

        header.setAlgorithm("RS256");
        header.setType("JWT");

        try {
            return base64Url(JSON_FACTORY.toString(header))
                + "." + base64Url(JSON_FACTORY.toString(payload))
                + "." + base64Url("signature");
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * A syntactically valid ID token with a signature that is only a placeholder. The issuer,
     * audience and expiry are all checked before the signature is, so a token built here is
     * refused for the reason under test rather than for its signature.
     */
    private String idToken(String issuer, String audience, Instant expiresAt) {
        String header = """
            {"alg":"RS256","kid":"test-key","typ":"JWT"}""";

        String payload = """
            {"iss":"%s","aud":"%s","sub":"112233445566778899000","email":"ada@gmail.com",\
            "email_verified":true,"nonce":"nonce","exp":%d,"iat":%d}"""
            .formatted(issuer, audience, expiresAt.getEpochSecond(), expiresAt.getEpochSecond() - 600);

        return base64Url(header) + "." + base64Url(payload) + "." + base64Url("not-a-real-signature");
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

}

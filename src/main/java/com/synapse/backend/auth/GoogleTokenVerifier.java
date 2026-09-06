package com.synapse.backend.auth;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.synapse.backend.auth.dto.GoogleClaims;
import com.synapse.backend.auth.exceptions.GoogleUnavailableException;
import com.synapse.backend.auth.exceptions.InvalidGoogleCredentialException;

/**
 * The one place a Google ID token is turned into claims Synapse trusts.
 *
 * <p>Verification is Google's own {@link GoogleIdTokenVerifier}, which checks the signature
 * against Google's rotating public keys, the issuer, the audience, and the expiry. What it
 * does not decide is added here: a non-empty subject, a verified email address, and the
 * nonce this application issued for the attempt. Everything past this class is trusted, and
 * nothing before it is: a token decoded by the frontend is a display convenience, never a
 * credential.</p>
 *
 * <p>It is a concrete class with one method so tests can replace it with a mock, which is
 * what keeps the suite off Google's live endpoints. Nothing else in the application talks to
 * Google.</p>
 */
@Service
public class GoogleTokenVerifier {

    private static final GsonFactory JSON_FACTORY = new GsonFactory();

    private final GoogleIdTokenVerifier verifier;

    @Autowired
    public GoogleTokenVerifier(GoogleAuthProperties googleAuthProperties) {
        List<String> clientIds = googleAuthProperties.clientIds();

        // A null audience tells the verifier to skip the audience check entirely, which would
        // accept a token minted for somebody else's application. An unconfigured client id
        // becomes an empty list instead, which matches nothing.
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), JSON_FACTORY)
            .setAudience(clientIds == null ? List.of() : clientIds)
            .build();
    }

    /**
     * Takes a ready-made verifier, so tests can exercise the claim checks below
     * against tokens Google never signed. Nothing in production calls this, which
     * is why the constructor above carries {@code @Autowired}: two constructors
     * and no annotation leaves Spring unable to pick one.
     */
    GoogleTokenVerifier(GoogleIdTokenVerifier verifier) {
        this.verifier = verifier;
    }

    /**
     * Verifies a Google ID token and returns the claims it carries.
     *
     * @param credential the raw ID token Google Identity Services gave the frontend.
     * @param expectedNonce the nonce Synapse issued for this attempt and has just consumed.
     * @return the verified subject, address, name, and hosted domain.
     * @throws InvalidGoogleCredentialException if any check fails, whichever one it was.
     * @throws GoogleUnavailableException if Google could not be reached to fetch its signing keys.
     */
    public GoogleClaims verify(String credential, String expectedNonce) {
        GoogleIdToken idToken = parsed(credential);

        if (!signedByGoogle(idToken))
            throw new InvalidGoogleCredentialException();

        GoogleIdToken.Payload payload = idToken.getPayload();

        if (payload.getSubject() == null || payload.getSubject().isBlank())
            throw new InvalidGoogleCredentialException();

        if (payload.getEmail() == null || payload.getEmail().isBlank())
            throw new InvalidGoogleCredentialException();

        if (!expectedNonce.equals(payload.getNonce()))
            throw new InvalidGoogleCredentialException();

        return new GoogleClaims(
            payload.getSubject(),
            payload.getEmail(),
            Boolean.TRUE.equals(payload.getEmailVerified()),
            (String) payload.get("name"),
            payload.getHostedDomain()
        );
    }

    /**
     * Reads the token's own structure, before anything is believed about it.
     *
     * <p>Kept apart from {@link #signedByGoogle} because both stages can raise an
     * {@link IOException} and they mean opposite things. Here it means the bytes
     * were never a JWT — malformed JSON inside an otherwise well-formed token
     * raises exactly this — which is the caller's fault and a plain rejection.
     * Folding the two together reported a garbage credential as a Google
     * outage.</p>
     */
    private GoogleIdToken parsed(String credential) {
        try {
            return GoogleIdToken.parse(JSON_FACTORY, credential);
        } catch (IOException | IllegalArgumentException ex) {
            throw new InvalidGoogleCredentialException();
        }
    }

    /**
     * Runs the signature, issuer, audience, and expiry checks.
     *
     * <p>An {@link IOException} here is the one case that is not the credential's
     * fault: it means Google's signing keys could not be fetched, so the token was
     * never judged at all and the caller should simply try again.</p>
     */
    private boolean signedByGoogle(GoogleIdToken idToken) {
        try {
            return verifier.verify(idToken);
        } catch (IOException ex) {
            throw new GoogleUnavailableException();
        } catch (GeneralSecurityException ex) {
            throw new InvalidGoogleCredentialException();
        }
    }

}

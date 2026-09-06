package com.synapse.backend.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.synapse.backend.auth.dto.GoogleClaims;
import com.synapse.backend.auth.dto.GoogleLoginRequest;
import com.synapse.backend.auth.dto.GoogleSignInResult;
import com.synapse.backend.auth.dto.LinkGoogleRequest;
import com.synapse.backend.auth.dto.LoginResponse;
import com.synapse.backend.auth.dto.LoginResult;
import com.synapse.backend.auth.dto.UnlinkGoogleRequest;
import com.synapse.backend.auth.exceptions.GoogleUnlinkNotAllowedException;
import com.synapse.backend.auth.exceptions.IncorrectPasswordException;
import com.synapse.backend.auth.exceptions.InvalidGoogleCredentialException;
import com.synapse.backend.auth.exceptions.PasswordNotSetException;
import com.synapse.backend.security.jwt.JwtService;
import com.synapse.backend.shared.ratelimit.RateLimitProperties;
import com.synapse.backend.shared.ratelimit.RateLimitService;
import com.synapse.backend.user.User;
import com.synapse.backend.user.UserRepository;
import com.synapse.backend.user.UserTimeZoneService;
import com.synapse.backend.user.exceptions.UserNotFoundException;

import jakarta.transaction.Transactional;

/**
 * Signs users in with Google, and attaches or removes a Google identity on request.
 *
 * <p>Google is an authentication method, never a session: a verified credential is exchanged
 * for the same Synapse access token and rotating refresh token a password login issues, and
 * the Google token is discarded. Nothing Google issues is ever accepted as a Synapse bearer
 * token, and no Google access token, refresh token, or raw ID token is stored.</p>
 *
 * <p>The identity is Google's {@code sub} claim, held in {@code app_user.google_subject}. The
 * address is not: a Google Account keeps its subject when its owner changes their Google
 * email, and a Synapse user can edit their Synapse email whenever they like. That is why a
 * login resolves by subject first and why a login never writes the email or name back from
 * Google.</p>
 *
 * <p>No method here is transactional, and that is deliberate. Verifying a credential can mean
 * fetching Google's signing keys over the network, and a transaction open across that call
 * holds a pooled database connection for as long as Google takes to answer. Every database
 * write therefore happens after verification, inside
 * {@link GoogleAccountPersistenceService}. Keep it that way: moving a repository call above
 * {@link #verifiedClaims} puts network latency back inside a connection's lifetime.</p>
 */
@Service
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenPersistenceService refreshTokenPersistenceService;
    private final GoogleAccountPersistenceService googleAccountPersistenceService;
    private final GoogleNonceService googleNonceService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final UserTimeZoneService userTimeZoneService;

    public GoogleAuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        RefreshTokenPersistenceService refreshTokenPersistenceService,
        GoogleAccountPersistenceService googleAccountPersistenceService,
        GoogleNonceService googleNonceService,
        GoogleTokenVerifier googleTokenVerifier,
        RateLimitService rateLimitService,
        RateLimitProperties rateLimitProperties,
        UserTimeZoneService userTimeZoneService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenPersistenceService = refreshTokenPersistenceService;
        this.googleAccountPersistenceService = googleAccountPersistenceService;
        this.googleNonceService = googleNonceService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.userTimeZoneService = userTimeZoneService;
    }

    /**
     * Issues a nonce for one sign-in attempt.
     *
     * <p>Limited far more loosely than signing in is. A nonce is taken on page
     * view rather than per attempt, and every visitor behind one NAT'd network
     * shares a client address, so a tight limit here would lock a whole campus
     * out of Google sign-in while password login carried on working.</p>
     *
     * @param clientIp the address the request came from.
     * @return the raw nonce, for the response body and the cookie that binds it to this browser.
     * @throws RateLimitExceededException if the address has asked for too many nonces.
     */
    public String issueNonce(String clientIp) {
        rateLimitService.check("google-nonce-ip:" + clientIp, rateLimitProperties.googleNonce());

        return googleNonceService.issueNonce();
    }

    /**
     * Signs somebody in with Google, creating or linking their account if this is the first time.
     *
     * <p>There is one endpoint rather than a sign-up and a sign-in, because the frontend
     * cannot know which one this is and Google will not tell it. The account is resolved
     * here, and the answer is the same {@code LoginResponse} and refresh cookie a password
     * login produces.</p>
     *
     * @param req the Google credential and the time zone a new account would be created in.
     * @param nonce the nonce from the client's cookie, or null if it was absent.
     * @param clientIp the address the request came from.
     * @return the user's name, email and an access token, plus a refresh token for the client cookie.
     * @throws InvalidGoogleCredentialException if the nonce or the credential does not check out.
     * @throws GoogleUnavailableException if Google could not be reached to verify the credential.
     * @throws GoogleEmailNotAuthoritativeException if Google does not own the address and no account is linked yet.
     * @throws GoogleAccountConflictException if the address belongs to an account linked to another Google Account.
     * @throws InvalidUserDetailsException if a time zone was supplied but is not a real IANA zone.
     * @throws RateLimitExceededException if the address has signed in too many times.
     */
    public LoginResult loginWithGoogle(GoogleLoginRequest req, String nonce, String clientIp) {
        rateLimitService.check("google-login-ip:" + clientIp, rateLimitProperties.googleLogin());

        String timeZone = userTimeZoneService.resolveOrDefault(req.timeZone());
        GoogleClaims claims = verifiedClaims(req.credential(), nonce);
        GoogleSignInResult session = googleAccountPersistenceService.signIn(claims, timeZone);
        User user = session.user();

        return new LoginResult(
            new LoginResponse(
                user.getName(),
                user.getEmail(),
                jwtService.generateAccessToken(user)
            ),
            session.refreshToken()
        );
    }

    /**
     * Attaches a Google identity to the signed-in account.
     *
     * <p>This is the flow for a Google address that is not the account's Synapse address,
     * which automatic linking deliberately refuses to guess at. Both identities are proven
     * here — the Synapse password and a fresh Google credential — so the two addresses do
     * not have to match, and neither is copied onto the other.</p>
     *
     * <p>The credential is verified before the account is loaded, so no database connection
     * is held while Google is being asked about it. The cost is that a wrong password still
     * spends the nonce, which is one extra round trip for somebody who mistyped.</p>
     *
     * <p>The password is checked here and the subject is written by a later statement, so the
     * hash it was checked against is carried into that write. A password reset landing in
     * between would otherwise be undone: somebody holding the old password and a still-valid
     * access token could attach their own Google Account moments after the owner recovered
     * it, leaving a way in that the reset was meant to close.</p>
     *
     * <p>Presenting the Google Account that is already linked changes nothing and succeeds,
     * so a client that retries does not have to unlink first.</p>
     *
     * @param userId the id of the authenticated user.
     * @param req the Google credential and the account's current password.
     * @param nonce the nonce from the client's cookie, or null if it was absent.
     * @param clientIp the address the request came from.
     * @throws UserNotFoundException if the user ID does not exist in DB.
     * @throws PasswordNotSetException if the account has no password to check against.
     * @throws IncorrectPasswordException if the current password does not match.
     * @throws InvalidGoogleCredentialException if the nonce or the credential does not check out.
     * @throws GoogleAccountConflictException if either identity is already spoken for.
     * @throws RateLimitExceededException if the address has tried too many times.
     */
    public void linkGoogle(Long userId, LinkGoogleRequest req, String nonce, String clientIp) {
        rateLimitService.check("google-login-ip:" + clientIp, rateLimitProperties.googleLogin());

        GoogleClaims claims = verifiedClaims(req.credential(), nonce);
        User user = authenticatedByPassword(userId, req.currentPassword());

        if (claims.subject().equals(user.getGoogleSubject()))
            return;

        googleAccountPersistenceService.linkWithPassword(userId, claims.subject(), user.getPasswordHash());
    }

    /**
     * Removes the Google identity from the signed-in account.
     *
     * <p>When a link is actually removed, every refresh token of the account goes with it.
     * Google was a way in, and a session somebody obtained through a Google Account that has
     * since been compromised would otherwise stay refreshable for thirty days after its owner
     * cut the link — which is exactly the situation somebody unlinking in a hurry is trying to
     * end. Password changes revoke sessions for the same reason, and this is the same event:
     * the ways into the account changed. Access tokens already issued are not revoked and last
     * out their fifteen minutes, as they do everywhere else.</p>
     *
     * <p>Unlinking an account that has no link removes nothing and therefore ends nothing. A
     * retried or duplicated request must not sign somebody out of every device for no reason,
     * so the revocation follows what the write actually did rather than the fact that it ran.
     * The account itself and everything in it are untouched either way. An account with no
     * password cannot do this at all, because the database would be left holding a row nobody
     * can sign in to, and the check constraint on {@code app_user} refuses the write in any
     * case.</p>
     *
     * @param userId the id of the authenticated user.
     * @param req the account's current password.
     * @return true when a link was removed and the caller's sessions have ended.
     * @throws UserNotFoundException if the user ID does not exist in DB.
     * @throws GoogleUnlinkNotAllowedException if the account signs in with Google only.
     * @throws IncorrectPasswordException if the current password does not match.
     */
    @Transactional
    public boolean unlinkGoogle(Long userId, UnlinkGoogleRequest req) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.hasPassword())
            throw new GoogleUnlinkNotAllowedException();

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash()))
            throw new IncorrectPasswordException();

        if (!googleAccountPersistenceService.unlink(userId))
            return false;

        refreshTokenPersistenceService.revokeAllRefreshTokens(userId);

        return true;
    }

    /**
     * Consumes the nonce, verifies the credential against it, and insists the address is one
     * Google has confirmed the holder can read.
     *
     * <p>The nonce is spent before the credential is judged, so a replayed token fails on
     * the second attempt however good its signature is.</p>
     */
    private GoogleClaims verifiedClaims(String credential, String nonce) {
        if (!googleNonceService.consumeNonce(nonce))
            throw new InvalidGoogleCredentialException();

        GoogleClaims claims = googleTokenVerifier.verify(credential, nonce);

        if (!claims.emailVerified())
            throw new InvalidGoogleCredentialException();

        return claims;
    }

    private User authenticatedByPassword(Long userId, String currentPassword) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.hasPassword())
            throw new PasswordNotSetException();

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash()))
            throw new IncorrectPasswordException();

        return user;
    }

}

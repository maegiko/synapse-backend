package com.synapse.backend.auth;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.synapse.backend.auth.dto.GoogleClaims;
import com.synapse.backend.auth.dto.GoogleSignInResult;
import com.synapse.backend.auth.exceptions.GoogleAccountConflictException;
import com.synapse.backend.auth.exceptions.GoogleEmailNotAuthoritativeException;
import com.synapse.backend.auth.exceptions.IncorrectPasswordException;
import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;
import com.synapse.backend.user.User;
import com.synapse.backend.user.UserNameService;
import com.synapse.backend.user.UserRepository;
import com.synapse.backend.user.exceptions.UserNotFoundException;

import jakarta.transaction.Transactional;

/**
 * Everything a verified Google credential does to the database.
 *
 * <p>Split from {@link GoogleAuthService} so the transaction starts only once the
 * credential has already been verified. Verifying one can mean fetching Google's
 * signing keys over the network, and doing that inside a transaction holds a
 * pooled connection for the duration of somebody else's HTTP request; production
 * runs a pool of five. Nothing in this class talks to Google.</p>
 */
@Service
public class GoogleAccountPersistenceService {

    /** What an account gets when Google's name claim has nothing left that could be a name. */
    private static final String FALLBACK_FULL_NAME = "Synapse User";

    /** Everything a name is not, replaced with a space before the name is capitalised. */
    private static final String NON_NAME_CHARACTERS = "[^\\p{L}\\p{M} '’-]";

    /** Domains Google runs itself, where a verified address is Google's to vouch for. */
    private static final String GMAIL_SUFFIX = "@gmail.com";
    private static final String GOOGLEMAIL_SUFFIX = "@googlemail.com";

    private static final String RACE_MESSAGE =
        "Another sign-in for this Google Account finished first. Try again.";

    private final UserRepository userRepository;
    private final EmailVerificationTokenPersistenceService emailVerificationTokenPersistenceService;
    private final RefreshTokenPersistenceService refreshTokenPersistenceService;
    private final UserNameService userNameService;

    public GoogleAccountPersistenceService(
        UserRepository userRepository,
        EmailVerificationTokenPersistenceService emailVerificationTokenPersistenceService,
        RefreshTokenPersistenceService refreshTokenPersistenceService,
        UserNameService userNameService
    ) {
        this.userRepository = userRepository;
        this.emailVerificationTokenPersistenceService = emailVerificationTokenPersistenceService;
        this.refreshTokenPersistenceService = refreshTokenPersistenceService;
        this.userNameService = userNameService;
    }

    /**
     * Resolves the account a verified credential belongs to and issues its session.
     *
     * <p>Both share one transaction, so an account that was created or claimed and
     * the refresh token minted for it commit together. A failure while issuing the
     * session leaves the account exactly as it was rather than claimed, stripped of
     * its password, and unusable.</p>
     *
     * @param claims the already-verified Google claims.
     * @param timeZone the zone a newly created or claimed account is given.
     * @return the resolved account and its raw refresh token.
     * @throws GoogleEmailNotAuthoritativeException if Google does not own the address and no account is linked yet.
     * @throws GoogleAccountConflictException if the address belongs to an account linked to another Google Account.
     */
    @Transactional
    public GoogleSignInResult signIn(GoogleClaims claims, String timeZone) {
        User user = resolveAccount(claims, timeZone);

        return new GoogleSignInResult(user, refreshTokenPersistenceService.issueRefreshToken(user.getId()));
    }

    /**
     * Attaches a Google subject to an account that has none.
     *
     * <p>The check and the write are one conditional update, so two credentials
     * racing to link the same account cannot both win. The unique index on
     * {@code google_subject} covers the other direction, where the subject already
     * belongs to somebody else.</p>
     *
     * @param userId the account to link.
     * @param googleSubject the verified Google subject.
     * @throws GoogleAccountConflictException if the account or the subject is already spoken for.
     */
    @Transactional
    public void link(Long userId, String googleSubject) {
        if (attempt(() -> userRepository.linkGoogleSubjectIfAbsent(userId, googleSubject)) == 0)
            throw new GoogleAccountConflictException(
                "This account is already linked to a different Google Account. Unlink that one first."
            );
    }

    /**
     * Runs a link update, translating the unique index into the conflict it means.
     * The index is the other half of the guarantee: the conditional update stops two
     * subjects reaching one account, and this stops one subject reaching two.
     */
    private long attempt(Supplier<Long> update) {
        try {
            return update.get();
        } catch (DataIntegrityViolationException ex) {
            throw new GoogleAccountConflictException("That Google Account is already linked to another account.");
        }
    }

    /**
     * Attaches a Google subject to an account whose password was just checked.
     *
     * <p>The observed hash rides along into the conditional update, so the check and
     * the write are one statement. Verifying a password and then writing are
     * otherwise two, and a password reset landing between them would be undone:
     * somebody holding the old password and a still-valid access token could
     * attach their own Google Account moments after the owner recovered it.</p>
     *
     * @param userId the account to link.
     * @param googleSubject the verified Google subject.
     * @param expectedPasswordHash the hash the supplied password was verified against.
     * @throws IncorrectPasswordException if the account's password changed while this ran.
     * @throws GoogleAccountConflictException if the account or the subject is already spoken for.
     */
    @Transactional
    public void linkWithPassword(Long userId, String googleSubject, String expectedPasswordHash) {
        long linked = attempt(
            () -> userRepository.linkGoogleSubjectIfPasswordUnchanged(userId, googleSubject, expectedPasswordHash)
        );

        if (linked > 0)
            return;

        // Nothing was written, and the two reasons need different answers. Only the
        // failure path pays for this read.
        User current = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        if (!expectedPasswordHash.equals(current.getPasswordHash()))
            throw new IncorrectPasswordException();

        throw new GoogleAccountConflictException(
            "This account is already linked to a different Google Account. Unlink that one first."
        );
    }

    /**
     * Removes the Google subject, and says whether there was one to remove.
     *
     * @param userId the account to unlink.
     * @return true when a link was actually removed, so the caller's sessions must end.
     */
    @Transactional
    public boolean unlink(Long userId) {
        return userRepository.unlinkGoogleSubjectIfPresent(userId) > 0;
    }

    /**
     * Finds, links, or creates the Synapse account a verified Google credential belongs to.
     *
     * <p>The subject is looked up first and wins outright. A Synapse account that has been
     * linked keeps its own email, name, and profile even when Google's current address is a
     * different one, and a login never writes any of them back, so editing a Synapse address
     * survives the next sign-in.</p>
     *
     * <p>Only when no account holds the subject does the address matter, and only then does
     * it have to be an address Google owns. Everything below this point is a first link.</p>
     */
    private User resolveAccount(GoogleClaims claims, String timeZone) {
        Optional<User> linked = userRepository.findByGoogleSubject(claims.subject());

        if (linked.isPresent())
            return linked.get();

        if (!isGoogleAuthoritative(claims))
            throw new GoogleEmailNotAuthoritativeException();

        String email = RequestText.normalisedEmail(claims.email());
        Optional<User> existing = userRepository.findByEmail(email);

        if (existing.isEmpty())
            return createGoogleAccount(claims, email, timeZone);

        User user = existing.get();

        if (user.getGoogleSubject() != null)
            throw new GoogleAccountConflictException(
                "That email address is already linked to a different Google Account."
            );

        if (user.isEmailVerified())
            return linkVerifiedAccount(user, claims.subject());

        return claimUnverifiedAccount(user, claims, timeZone);
    }

    /**
     * Whether Google is entitled to hand this address to whoever holds the Google Account.
     *
     * <p>A Gmail address and a Workspace address are administered by Google, so a Google
     * Account holding one owns it. A Google Account built on a third-party address does not:
     * Google confirmed the person could read that inbox once, at some point, and has no say
     * over who reads it now. Letting one of those create or claim a Synapse account would
     * hand Google's sign-up check the job Synapse's own verification email does.</p>
     *
     * <p>The {@code email_verified} half of the rule is checked in {@code GoogleAuthService},
     * which refuses an unverified address on every path, not only this one.</p>
     */
    private boolean isGoogleAuthoritative(GoogleClaims claims) {
        String email = RequestText.normalisedEmail(claims.email());

        return email.endsWith(GMAIL_SUFFIX)
            || email.endsWith(GOOGLEMAIL_SUFFIX)
            || (claims.hostedDomain() != null && !claims.hostedDomain().isBlank());
    }

    /** A brand new passwordless account, verified from the moment it exists. */
    private User createGoogleAccount(GoogleClaims claims, String email, String timeZone) {
        User user = new User(googleFullName(claims.name()), email, null, claims.subject(), timeZone);

        user.markEmailVerified(LocalDateTime.now(ZoneOffset.UTC));

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new GoogleAccountConflictException(RACE_MESSAGE);
        }
    }

    /**
     * Adds Google to an account that has already proven it owns this address.
     *
     * <p>The subject is attached with the same conditional update explicit linking
     * uses, so two first logins for one address cannot each link a different Google
     * Account to it. The password, name, time zone, content and other sessions are
     * all left alone.</p>
     */
    private User linkVerifiedAccount(User user, String googleSubject) {
        link(user.getId(), googleSubject);

        return userRepository.findById(user.getId())
            .orElseThrow(() -> new GoogleAccountConflictException(RACE_MESSAGE));
    }

    /**
     * Takes over an account that registered with this address but never confirmed it.
     *
     * <p>Google has just proven who owns the address, and whoever set that password had not.
     * The password may well belong to somebody who registered the victim's address before
     * they got to it, so it is dropped rather than kept alongside the new Google identity,
     * and every registration link still outstanding is invalidated so a copy sitting in the
     * inbox cannot mint a session afterwards. The name and time zone were part of the same
     * unproven registration and are replaced for the same reason.</p>
     *
     * <p>The subject is attached first, by the conditional update, so a second credential
     * racing for the same unclaimed account is refused instead of overwriting it. Invalidating
     * the links clears the persistence context, which is why the account is re-read before the
     * rest of the claim is written.</p>
     */
    private User claimUnverifiedAccount(User user, GoogleClaims claims, String timeZone) {
        link(user.getId(), claims.subject());

        emailVerificationTokenPersistenceService.invalidateRegistrationTokens(user.getId());

        User claimed = userRepository.findById(user.getId())
            .orElseThrow(() -> new GoogleAccountConflictException(RACE_MESSAGE));

        claimed.markEmailVerified(LocalDateTime.now(ZoneOffset.UTC));
        claimed.clearPasswordHash();
        claimed.updateFullName(googleFullName(claims.name()));
        claimed.updateTimeZone(timeZone);

        return userRepository.saveAndFlush(claimed);
    }

    /**
     * The name to store for an account Google created or claimed.
     *
     * <p>Google's name claim is a display string from somebody else's system, so it is cut
     * to the characters a Synapse name is allowed to contain and then capitalised exactly as
     * a typed name is. A claim with nothing name-shaped left in it falls back to a
     * placeholder the user can edit on their profile, because the column is not nullable and
     * refusing the sign-in over a display string would be worse.</p>
     */
    private String googleFullName(String name) {
        String capitalised = userNameService.capitalised(
            name == null ? "" : name.replaceAll(NON_NAME_CHARACTERS, " ")
        );

        return capitalised.length() < ValidationLimits.FULL_NAME_MIN
            ? FALLBACK_FULL_NAME
            : RequestText.clamped(capitalised, ValidationLimits.FULL_NAME_MAX);
    }

}

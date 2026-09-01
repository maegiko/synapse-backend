package com.synapse.backend.auth;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.synapse.backend.auth.dto.ChangePasswordRequest;
import com.synapse.backend.auth.dto.LoginRequest;
import com.synapse.backend.auth.dto.LoginResponse;
import com.synapse.backend.auth.dto.LoginResult;
import com.synapse.backend.auth.dto.RefreshResponse;
import com.synapse.backend.auth.dto.RefreshResult;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.auth.dto.RegisterResponse;
import com.synapse.backend.auth.exceptions.EmailAlreadyExistsException;
import com.synapse.backend.auth.exceptions.EmailNotVerifiedException;
import com.synapse.backend.auth.exceptions.IncorrectPasswordException;
import com.synapse.backend.auth.exceptions.InvalidRefreshTokenException;
import com.synapse.backend.auth.exceptions.LoginFailException;
import com.synapse.backend.security.jwt.JwtService;
import com.synapse.backend.shared.ratelimit.RateLimitProperties;
import com.synapse.backend.shared.ratelimit.RateLimitService;
import com.synapse.backend.user.User;
import com.synapse.backend.user.UserNameService;
import com.synapse.backend.user.UserRepository;
import com.synapse.backend.user.UserTimeZoneService;
import com.synapse.backend.user.exceptions.UserNotFoundException;

import jakarta.transaction.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenPersistenceService refreshTokenPersistenceService;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final UserTimeZoneService userTimeZoneService;
    private final UserNameService userNameService;
    private final EmailVerificationService emailVerificationService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        RefreshTokenPersistenceService refreshTokenPersistenceService,
        RateLimitService rateLimitService,
        RateLimitProperties rateLimitProperties,
        UserTimeZoneService userTimeZoneService,
        UserNameService userNameService,
        EmailVerificationService emailVerificationService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenPersistenceService = refreshTokenPersistenceService;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.userTimeZoneService = userTimeZoneService;
        this.userNameService = userNameService;
        this.emailVerificationService = emailVerificationService;
    }

    /**
     * Registers an unverified user and emails them a verification link.
     *
     * <p>Registration no longer signs anybody in: no access token is returned and
     * no refresh token is issued, because the account cannot be used until the
     * emailed link is confirmed.</p>
     *
     * <p>The full name is stored with each word capitalised, so a name typed in a
     * hurry still reads like a name everywhere it is shown.</p>
     *
     * <p>An address that already belongs to a verified account is rejected with a
     * conflict, exactly as before. An address that belongs to an account that is
     * still unverified gets a replacement link instead, and its stored password,
     * name, and time zone are left untouched, so registering over a pending
     * account can neither take it over nor reveal that it exists.</p>
     *
     * <p>The account and its token are saved before the provider is called, so a
     * failed send leaves an account the resend endpoint can recover rather than an
     * account nobody can ever verify.</p>
     *
     * @param registerRequest validated registration details.
     * @param clientIp the address the request came from.
     * @return the address the verification link was sent to.
     * @throws EmailAlreadyExistsException if the email already belongs to a verified account.
     * @throws InvalidUserDetailsException if a time zone was supplied but is not a real IANA zone.
     * @throws RateLimitExceededException if the address has registered too many accounts.
     * @throws EmailProviderException if the verification email could not be sent.
     */
    public RegisterResponse registerUser(RegisterRequest registerRequest, String clientIp) {
        rateLimitService.check("register:" + clientIp, rateLimitProperties.register());

        String email = registerRequest.email().trim().toLowerCase(Locale.ROOT);
        String fullName = userNameService.capitalised(registerRequest.fullName());
        String timeZone = userTimeZoneService.resolveOrDefault(registerRequest.timeZone());
        Optional<User> existingUser = findUserByEmail(email);

        if (existingUser.isPresent()) {
            if (existingUser.get().isEmailVerified())
                throw new EmailAlreadyExistsException(email);

            emailVerificationService.sendRegistrationVerification(existingUser.get());

            return registrationAccepted(email);
        }

        String passwordHash = passwordEncoder.encode(registerRequest.password());

        User newUser = userRepository.save(new User(
            fullName,
            email,
            passwordHash,
            timeZone
        ));

        emailVerificationService.sendRegistrationVerification(newUser);

        return registrationAccepted(email);
    }

    private RegisterResponse registrationAccepted(String email) {
        return new RegisterResponse(
            email,
            "Check your email for a verification link to finish creating your account."
        );
    }

    /**
     * Logs in a user and returns their details with an access token.
     *
     * @param loginRequest validated login details.
     * @param clientIp the address the request came from.
     * @return the user's name, email and an access token, plus a refresh token for the client cookie.
     * @throws LoginFailException if the email address is not registered or the password is incorrect.
     * @throws EmailNotVerifiedException if the credentials are correct but the address is not verified yet.
     * @throws RateLimitExceededException if the email or the address has made too many login attempts.
     */
    public LoginResult loginUser(LoginRequest loginRequest, String clientIp) {
        String email = loginRequest.email().trim().toLowerCase(Locale.ROOT);
        String password = loginRequest.password();

        rateLimitService.check("login-email:" + email, rateLimitProperties.login());
        rateLimitService.check("login-ip:" + clientIp, rateLimitProperties.login());

        User user = findUserByEmail(email).orElseThrow(() -> new LoginFailException());

        if (!doesPasswordMatch(password, user.getPasswordHash()))
            throw new LoginFailException();

        if (!user.isEmailVerified())
            throw new EmailNotVerifiedException();

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenPersistenceService.issueRefreshToken(user.getId());

        return new LoginResult(
            new LoginResponse(
                user.getName(),
                user.getEmail(),
                accessToken
            ),
            refreshToken
        );
    }

    /**
     * Issues a new access token from a refresh token and rotates the refresh token.
     *
     * <p>The presented refresh token is revoked before a replacement is issued, so
     * each refresh token can only be used once. Revoking the old token and saving
     * the new one share one transaction, so a failure partway through leaves the
     * presented token usable instead of logging the user out.</p>
     *
     * @param refreshToken the raw refresh token from the client cookie, or null if absent.
     * @return a new access token, plus the replacement refresh token for the client cookie.
     * @throws InvalidRefreshTokenException if the token is missing, unknown, revoked, or expired.
     */
    @Transactional
    public RefreshResult refreshAccessToken(String refreshToken) {
        if (refreshToken == null)
            throw new InvalidRefreshTokenException();

        Long userId = refreshTokenPersistenceService.consumeRefreshToken(refreshToken);
        User user = userRepository.findById(userId).orElseThrow(() -> new InvalidRefreshTokenException());

        String accessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = refreshTokenPersistenceService.issueRefreshToken(user.getId());

        return new RefreshResult(
            new RefreshResponse(accessToken),
            newRefreshToken
        );
    }

    /**
     * Logs out a user by revoking the refresh token they presented.
     *
     * <p>Missing, unknown, and already revoked tokens are ignored so repeated
     * logout requests succeed.</p>
     *
     * @param refreshToken the raw refresh token from the client cookie, or null if absent.
     */
    public void logoutUser(String refreshToken) {
        if (refreshToken == null)
            return;

        refreshTokenPersistenceService.revokeRefreshToken(refreshToken);
    }

    /**
     * Changes the password of the authenticated user.
     *
     * <p>Every active refresh token of the user is revoked once the new password
     * is saved, so sessions started with the old password cannot be refreshed.
     * The caller is expected to discard its access token as well.</p>
     *
     * @param userId the id of the authenticated user.
     * @param changePasswordRequest the validated current and new passwords.
     * @throws UserNotFoundException if the user ID does not exist in DB.
     * @throws IncorrectPasswordException if the current password does not match.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest changePasswordRequest) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        if (!doesPasswordMatch(changePasswordRequest.currentPassword(), user.getPasswordHash()))
            throw new IncorrectPasswordException();

        user.updatePasswordHash(passwordEncoder.encode(changePasswordRequest.newPassword()));
        userRepository.save(user);

        refreshTokenPersistenceService.revokeAllRefreshTokens(userId);
    }

    private Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    private boolean doesPasswordMatch(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }

}

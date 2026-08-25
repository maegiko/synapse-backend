package com.synapse.backend.auth;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.synapse.backend.auth.dto.LoginRequest;
import com.synapse.backend.auth.dto.LoginResponse;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.auth.dto.RegisterResponse;
import com.synapse.backend.auth.exceptions.EmailAlreadyExistsException;
import com.synapse.backend.auth.exceptions.LoginFailException;
import com.synapse.backend.security.jwt.JwtService;
import com.synapse.backend.shared.ratelimit.RateLimitProperties;
import com.synapse.backend.shared.ratelimit.RateLimitService;
import com.synapse.backend.user.User;
import com.synapse.backend.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        RateLimitService rateLimitService,
        RateLimitProperties rateLimitProperties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * Registers a new user and returns their details with an access token.
     *
     * @param registerRequest validated registration details.
     * @param clientIp the address the request came from.
     * @return the user's name, email and an access token.
     * @throws EmailAlreadyExistsException if email is already registered.
     * @throws RateLimitExceededException if the address has registered too many accounts.
     */
    public RegisterResponse registerUser(RegisterRequest registerRequest, String clientIp) {
        rateLimitService.check("register:" + clientIp, rateLimitProperties.register());

        String email = registerRequest.email().trim().toLowerCase(Locale.ROOT);

        if (isEmailInUse(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        String passwordHash = passwordEncoder.encode(registerRequest.password());

        User user = new User(
            registerRequest.fullName(),
            email,
            passwordHash
        );

        User newUser = userRepository.save(user);
        String accessToken = jwtService.generateAccessToken(newUser);

        return new RegisterResponse(
            newUser.getName(),
            newUser.getEmail(),
            accessToken
        );
    }

    private boolean isEmailInUse(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Logs in a user and returns their details with an access token.
     *
     * @param loginRequest validated login details.
     * @param clientIp the address the request came from.
     * @return the user's name, email and an access token.
     * @throws LoginFailException if the email address is not registered or the password is incorrect.
     * @throws RateLimitExceededException if the email or the address has made too many login attempts.
     */
    public LoginResponse loginUser(LoginRequest loginRequest, String clientIp) {
        String email = loginRequest.email().trim().toLowerCase(Locale.ROOT);
        String password = loginRequest.password();

        rateLimitService.check("login-email:" + email, rateLimitProperties.login());
        rateLimitService.check("login-ip:" + clientIp, rateLimitProperties.login());

        User user = findUserByEmail(email).orElseThrow(() -> new LoginFailException());

        if (!doesPasswordMatch(password, user.getPasswordHash()))
            throw new LoginFailException();

        String accessToken = jwtService.generateAccessToken(user);

        return new LoginResponse(
            user.getName(),
            user.getEmail(),
            accessToken
        );
    }

    private Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    private boolean doesPasswordMatch(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }

}

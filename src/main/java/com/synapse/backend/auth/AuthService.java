package com.synapse.backend.auth;

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
import com.synapse.backend.user.User;
import com.synapse.backend.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Registers a new user and returns their details with an access token.
     *
     * @param registerRequest validated registration details.
     * @return the user's name, email and an access token.
     * @throws EmailAlreadyExistsException if email is already registered.
     */
    public RegisterResponse registerUser(RegisterRequest registerRequest) {
        if (isEmailInUse(registerRequest.email())) {
            throw new EmailAlreadyExistsException(registerRequest.email());
        }

        String passwordHash = passwordEncoder.encode(registerRequest.password());

        User user = new User(
            registerRequest.fullName(),
            registerRequest.email(),
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
     * @return the user's name, email and an access token.
     * @throws LoginFailException if the email address is not registered or the password is incorrect.
     */
    public LoginResponse loginUser(LoginRequest loginRequest) {
        String email = loginRequest.email();
        String password = loginRequest.password();

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

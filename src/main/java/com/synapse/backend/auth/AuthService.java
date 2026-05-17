package com.synapse.backend.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.auth.dto.RegisterResponse;
import com.synapse.backend.auth.exceptions.EmailAlreadyExistsException;
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

    public RegisterResponse registerUser(RegisterRequest registerRequest) {
        if (isEmailInUse(registerRequest.email())) {
            throw new EmailAlreadyExistsException(registerRequest.email());
        }

        String passwordHash = passwordEncoder.encode(registerRequest.password());

        User user = new User(
            registerRequest.name(),
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

}

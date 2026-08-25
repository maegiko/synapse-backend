package com.synapse.backend.auth;

import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.auth.dto.LoginRequest;
import com.synapse.backend.auth.dto.LoginResponse;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.auth.dto.RegisterResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(
        summary = "Register a new user",
        description = "Creates a user account and returns a JWT access token.",
        responses = {
            @ApiResponse(responseCode = "201", description = "User registered"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid registration request",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "Email is already registered",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "429",
                description = "Too many registrations from this address",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<RegisterResponse> register(
        @Valid @RequestBody RegisterRequest registerRequest,
        HttpServletRequest httpRequest
    ) {
        RegisterResponse res = authService.registerUser(registerRequest, httpRequest.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @PostMapping("/login")
    @Operation(
        summary = "Log in a user",
        description = "Logs in a user and returns a JWT access token.",
        responses = {
            @ApiResponse(responseCode = "200", description = "User logged in"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid login request",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Invalid credentials",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "429",
                description = "Too many login attempts",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<LoginResponse> login(
        @Valid @RequestBody LoginRequest loginRequest,
        HttpServletRequest httpRequest
    ) {
        LoginResponse res = authService.loginUser(loginRequest, httpRequest.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

}

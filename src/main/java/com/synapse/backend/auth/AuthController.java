package com.synapse.backend.auth;

import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.auth.dto.RegisterResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
            )
        }
    )
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        RegisterResponse res = authService.registerUser(registerRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

}

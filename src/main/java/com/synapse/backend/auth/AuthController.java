package com.synapse.backend.auth;

import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.auth.dto.ChangePasswordRequest;
import com.synapse.backend.auth.dto.LoginRequest;
import com.synapse.backend.auth.dto.LoginResponse;
import com.synapse.backend.auth.dto.LoginResult;
import com.synapse.backend.auth.dto.RefreshResponse;
import com.synapse.backend.auth.dto.RefreshResult;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.auth.dto.RegisterResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;

    public AuthController(AuthService authService, RefreshCookieFactory refreshCookieFactory) {
        this.authService = authService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/register")
    @Operation(
        summary = "Register a new user",
        description = "Creates an unverified account and emails a verification link to the address. No access "
            + "token is returned and no refresh cookie is set: the account cannot be used until the link is "
            + "confirmed with POST /api/auth/email/verify. An address that already belongs to an unverified "
            + "account is sent a replacement link and gets this same response.",
        responses = {
            @ApiResponse(responseCode = "202", description = "Verification email sent"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid registration request",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "Email is already registered to a verified account",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "429",
                description = "Too many registrations from this address",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "502",
                description = "The verification email could not be sent",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<RegisterResponse> register(
        @Valid @RequestBody RegisterRequest registerRequest,
        HttpServletRequest httpRequest
    ) {
        RegisterResponse res = authService.registerUser(registerRequest, httpRequest.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(res);
    }

    @PostMapping("/login")
    @Operation(
        summary = "Log in a user",
        description = "Logs in a user, returns a JWT access token, and sets a refresh token cookie. An account "
            + "whose email address has not been verified cannot log in, even with the correct password.",
        responses = {
            @ApiResponse(responseCode = "200", description = "User logged in"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid login request",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Invalid credentials, or the email address is not verified yet",
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
        LoginResult res = authService.loginUser(loginRequest, httpRequest.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.OK)
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.issued(res.refreshToken()).toString())
            .body(res.response());
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh an access token",
        description = "Exchanges the refresh token cookie for a new access token and rotates the refresh token.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Access token refreshed"),
            @ApiResponse(
                responseCode = "401",
                description = "Missing, expired, revoked, or already used refresh token",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<RefreshResponse> refresh(
        @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        RefreshResult res = authService.refreshAccessToken(refreshToken);

        return ResponseEntity.status(HttpStatus.OK)
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.issued(res.refreshToken()).toString())
            .body(res.response());
    }

    @PostMapping("/logout")
    @Operation(
        summary = "Log out a user",
        description = "Revokes the refresh token cookie and clears it from the client.",
        responses = {
            @ApiResponse(responseCode = "204", description = "User logged out")
        }
    )
    public ResponseEntity<Void> logout(
        @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        authService.logoutUser(refreshToken);

        return ResponseEntity.status(HttpStatus.NO_CONTENT)
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.cleared().toString())
            .build();
    }

    @PutMapping("/password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Change the current user's password",
        description = "Changes the password of the currently authenticated user, revokes all of their refresh "
            + "tokens, and clears the refresh token cookie. The client must discard its access token.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Password changed"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid password change request",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user or incorrect current password",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "User not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> changePassword(
        @Valid @RequestBody ChangePasswordRequest changePasswordRequest,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        authService.changePassword(userId, changePasswordRequest);

        return ResponseEntity.status(HttpStatus.NO_CONTENT)
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.cleared().toString())
            .build();
    }

}

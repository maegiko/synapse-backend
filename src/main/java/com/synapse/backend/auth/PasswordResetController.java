package com.synapse.backend.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.auth.dto.ForgotPasswordRequest;
import com.synapse.backend.auth.dto.ResetPasswordRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth/password")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final RefreshCookieFactory refreshCookieFactory;

    public PasswordResetController(
        PasswordResetService passwordResetService,
        RefreshCookieFactory refreshCookieFactory
    ) {
        this.passwordResetService = passwordResetService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/forgot")
    @Operation(
        summary = "Email a password reset link",
        description = "Sends a single-use reset link to the address, which opens the frontend reset page. The "
            + "response is always 204, whether the address is unknown, belongs to an account that has never been "
            + "verified, or belongs to a live account, and it stays 204 even when the email provider fails, so the "
            + "endpoint never reveals who has an account. Only a verified account is actually sent a link, and a "
            + "new link invalidates the previous one.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Request accepted"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid email address",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "429",
                description = "Too many requests for this email address or from this client address",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> forgotPassword(
        @Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest,
        HttpServletRequest httpRequest
    ) {
        passwordResetService.requestReset(forgotPasswordRequest.email(), httpRequest.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/reset")
    @Operation(
        summary = "Set a new password from a reset link",
        description = "Consumes the single-use token from a reset email and sets the new password. Every refresh "
            + "token of that user is revoked and the caller's refresh cookie is cleared, so all sessions have to "
            + "sign in again; an access token issued before the reset is not blacklisted and stays valid until it "
            + "expires. The call does not sign the caller in, so the client should route to login afterwards. "
            + "Missing, unknown, expired, replaced, and already used tokens all fail the same way, and a token from "
            + "a verification email is never accepted here.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Password changed"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid password, or a missing, unknown, expired, replaced, or already used token",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        passwordResetService.resetPassword(resetPasswordRequest.token(), resetPasswordRequest.newPassword());

        return ResponseEntity.status(HttpStatus.NO_CONTENT)
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.cleared().toString())
            .build();
    }

}

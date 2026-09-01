package com.synapse.backend.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.auth.dto.ResendVerificationRequest;
import com.synapse.backend.auth.dto.VerifyEmailRequest;
import com.synapse.backend.auth.dto.VerifyEmailResponse;
import com.synapse.backend.auth.dto.VerifyEmailResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth/email")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;
    private final RefreshCookieFactory refreshCookieFactory;

    public EmailVerificationController(
        EmailVerificationService emailVerificationService,
        RefreshCookieFactory refreshCookieFactory
    ) {
        this.emailVerificationService = emailVerificationService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/verify")
    @Operation(
        summary = "Confirm an emailed verification link",
        description = "Consumes the single-use token from a verification email and reports which kind of link it "
            + "was in the response's kind property. A REGISTRATION link marks the account verified and signs it "
            + "in, returning an access token and setting the same refresh cookie login does. An EMAIL_CHANGE link "
            + "moves the account to its new address and returns only that address: no token and no cookie, because "
            + "the person confirming it normally already has a session. Clients must branch on kind rather than on "
            + "whether the visitor is already signed in. Missing, unknown, expired, replaced, and already used "
            + "tokens all fail the same way.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Email address confirmed"),
            @ApiResponse(
                responseCode = "400",
                description = "Missing, unknown, expired, replaced, or already used token",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "Another account has claimed the new email address",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<VerifyEmailResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest verifyEmailRequest) {
        VerifyEmailResult res = emailVerificationService.verifyEmail(verifyEmailRequest.token());

        if (res.refreshToken() == null)
            return ResponseEntity.status(HttpStatus.OK).body(res.response());

        return ResponseEntity.status(HttpStatus.OK)
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.issued(res.refreshToken()).toString())
            .body(res.response());
    }

    @PostMapping("/resend")
    @Operation(
        summary = "Resend a registration verification link",
        description = "Sends a replacement registration link to an address that is still unverified, which "
            + "invalidates the previous link. The response is always 204, whether the address is unknown, "
            + "already verified, or pending, so the endpoint never reveals who has an account.",
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
            ),
            @ApiResponse(
                responseCode = "502",
                description = "The verification email could not be sent",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> resendVerification(
        @Valid @RequestBody ResendVerificationRequest resendVerificationRequest,
        HttpServletRequest httpRequest
    ) {
        emailVerificationService.resendRegistrationVerification(
            resendVerificationRequest.email(),
            httpRequest.getRemoteAddr()
        );

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

}

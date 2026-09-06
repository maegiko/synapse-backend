package com.synapse.backend.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.auth.dto.GoogleLoginRequest;
import com.synapse.backend.auth.dto.GoogleNonceResponse;
import com.synapse.backend.auth.dto.LoginResponse;
import com.synapse.backend.auth.dto.LoginResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth/google")
public class GoogleAuthController {

    private static final String NONCE_COOKIE_NAME = "googleNonce";

    private final GoogleAuthService googleAuthService;
    private final GoogleNonceCookieFactory googleNonceCookieFactory;
    private final RefreshCookieFactory refreshCookieFactory;

    public GoogleAuthController(
        GoogleAuthService googleAuthService,
        GoogleNonceCookieFactory googleNonceCookieFactory,
        RefreshCookieFactory refreshCookieFactory
    ) {
        this.googleAuthService = googleAuthService;
        this.googleNonceCookieFactory = googleNonceCookieFactory;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/nonce")
    @Operation(
        summary = "Start a Google sign-in",
        description = "Issues a single-use nonce for one \"Continue with Google\" attempt and sets the same "
            + "value as a host-only HttpOnly cookie. The frontend passes the returned nonce to Google Identity "
            + "Services, and Google copies it into the ID token it signs, so the credential can only be spent "
            + "by the browser that asked for it. The nonce expires after a few minutes and is accepted once. "
            + "Call this with credentials: \"include\" so the cookie is stored.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Nonce issued"),
            @ApiResponse(
                responseCode = "429",
                description = "Too many sign-in attempts from this address",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<GoogleNonceResponse> issueNonce(HttpServletRequest httpRequest) {
        String nonce = googleAuthService.issueNonce(httpRequest.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.OK)
            .header(HttpHeaders.SET_COOKIE, googleNonceCookieFactory.issued(nonce).toString())
            .body(new GoogleNonceResponse(nonce));
    }

    @PostMapping
    @Operation(
        summary = "Continue with Google",
        description = "Verifies a Google ID token and answers with the same access token and refresh cookie a "
            + "password login does. There is no separate Google registration route: the backend decides whether "
            + "this is a new account, a link to an existing one, or a returning sign-in. A new account is created "
            + "passwordless and already verified, and gets no verification email. An address that already belongs "
            + "to a Synapse account is linked to it, keeping its password, content, settings, and other sessions; "
            + "an account that had registered but never confirmed itself is claimed instead, which clears the "
            + "password it was registered with. Automatic creation and linking only happen for an address Google "
            + "owns, meaning a Gmail address or a Google Workspace one; a Google Account built on a third-party "
            + "address has to register with Synapse and link Google afterwards. Requires the nonce cookie from "
            + "POST /api/auth/google/nonce, so call it with credentials: \"include\".",
        responses = {
            @ApiResponse(responseCode = "200", description = "User signed in"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid request, or a Google Account whose address Google does not own",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Missing, expired, replayed, or otherwise unverifiable Google credential",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "The address belongs to an account linked to a different Google Account",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "429",
                description = "Too many sign-in attempts from this address",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "502",
                description = "Google could not be reached to verify the credential",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<LoginResponse> loginWithGoogle(
        @Valid @RequestBody GoogleLoginRequest googleLoginRequest,
        @CookieValue(name = NONCE_COOKIE_NAME, required = false) String nonce,
        HttpServletRequest httpRequest
    ) {
        LoginResult res = googleAuthService.loginWithGoogle(googleLoginRequest, nonce, httpRequest.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.OK)
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.issued(res.refreshToken()).toString())
            .header(HttpHeaders.SET_COOKIE, googleNonceCookieFactory.cleared().toString())
            .body(res.response());
    }

}

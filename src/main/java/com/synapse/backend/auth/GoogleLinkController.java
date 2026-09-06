package com.synapse.backend.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.auth.dto.LinkGoogleRequest;
import com.synapse.backend.auth.dto.UnlinkGoogleRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * The authenticated half of Google sign-in, mapped under {@code /api/user} next to the other
 * account settings routes rather than under {@code /api/auth}, which is public.
 */
@RestController
@RequestMapping("/api/user")
@SecurityRequirement(name = "bearerAuth")
public class GoogleLinkController {

    private static final String NONCE_COOKIE_NAME = "googleNonce";

    private final GoogleAuthService googleAuthService;
    private final GoogleNonceCookieFactory googleNonceCookieFactory;
    private final RefreshCookieFactory refreshCookieFactory;

    public GoogleLinkController(
        GoogleAuthService googleAuthService,
        GoogleNonceCookieFactory googleNonceCookieFactory,
        RefreshCookieFactory refreshCookieFactory
    ) {
        this.googleAuthService = googleAuthService;
        this.googleNonceCookieFactory = googleNonceCookieFactory;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/google-link")
    @Operation(
        summary = "Link a Google Account",
        description = "Attaches a Google identity to the signed-in account, so it can afterwards sign in either "
            + "way. This is how a Google address that is not the account's Synapse address gets linked, which "
            + "\"Continue with Google\" deliberately will not guess at. It needs a live session, a fresh Google "
            + "credential and its nonce cookie, and the account's current password. The two addresses do not have "
            + "to match and neither is copied onto the other. Presenting the Google Account that is already linked "
            + "changes nothing and still returns 204.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Google Account linked"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid request body",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user, wrong password, or an unverifiable Google credential",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "User not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "That Google Account is linked elsewhere, or this account already has another one",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "429",
                description = "Too many attempts from this address",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "502",
                description = "Google could not be reached to verify the credential",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> linkGoogle(
        @Valid @RequestBody LinkGoogleRequest linkGoogleRequest,
        @CookieValue(name = NONCE_COOKIE_NAME, required = false) String nonce,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest httpRequest
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        googleAuthService.linkGoogle(userId, linkGoogleRequest, nonce, httpRequest.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.NO_CONTENT)
            .header(HttpHeaders.SET_COOKIE, googleNonceCookieFactory.cleared().toString())
            .build();
    }

    @DeleteMapping("/google-link")
    @Operation(
        summary = "Unlink the Google Account",
        description = "Removes the Google identity from the signed-in account, which keeps its password, "
            + "content and settings. Every refresh token of the account is revoked and the caller's refresh "
            + "cookie is cleared, so all sessions have to sign in again: a session obtained through a Google "
            + "Account that has since been compromised must not outlive the link. An access token issued before "
            + "the unlink is not blacklisted and stays valid until it expires. The current password is required, "
            + "and an account that signs in with Google only is refused, because unlinking would leave it with "
            + "no way in; such an account can set a password through the forgotten-password flow first. "
            + "Unlinking an account that has no link removes nothing, so it ends no sessions and clears no cookie, "
            + "and still returns 204: a retried request must not sign somebody out for nothing.",
        responses = {
            @ApiResponse(
                responseCode = "204",
                description = "Google Account unlinked and every session revoked, or nothing was linked"
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid request body",
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
            ),
            @ApiResponse(
                responseCode = "409",
                description = "The account has no password, so Google is its only way in",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> unlinkGoogle(
        @Valid @RequestBody UnlinkGoogleRequest unlinkGoogleRequest,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        if (!googleAuthService.unlinkGoogle(userId, unlinkGoogleRequest))
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

        return ResponseEntity.status(HttpStatus.NO_CONTENT)
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.cleared().toString())
            .build();
    }

}

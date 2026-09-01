package com.synapse.backend.user;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.user.dto.ChangeEmailRequest;
import com.synapse.backend.user.dto.EmailChangeResponse;
import com.synapse.backend.user.dto.UpdateUserDetailsRequest;
import com.synapse.backend.user.dto.UserDetailsResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/api/user")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/details")
    @Operation(
        summary = "Get user details",
        description = "Gets the details of the currently logged in user, including the IANA time zone "
            + "every calendar-day calculation is made in.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Got user details"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "User not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<UserDetailsResponse> getUserDetails(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        UserDetailsResponse res = userService.getUserDetails(userId);

        return ResponseEntity.ok(res);
    }

    @PatchMapping("/details")
    @Operation(
        summary = "Update user details",
        description = "Updates the full name and/or IANA time zone of the currently logged in user. Only the "
            + "supplied fields are changed and at least one must be supplied. The email address cannot be "
            + "changed here; POST /api/user/email-change starts a confirmed change instead. A new time "
            + "zone moves every later calendar-day boundary, such as streak days and deck due dates, but "
            + "never rewrites recorded days or stored timestamps.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Updated user details"),
            @ApiResponse(
                responseCode = "400",
                description = "No field supplied, or a supplied field is invalid",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "User not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<UserDetailsResponse> updateUserDetails(
        @RequestBody @Valid UpdateUserDetailsRequest req,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        UserDetailsResponse res = userService.updateUserDetails(userId, req);

        return ResponseEntity.ok(res);
    }

    @PostMapping("/email-change")
    @Operation(
        summary = "Request a change of email address",
        description = "Emails a single-use confirmation link to the proposed address. The account keeps its "
            + "current address, and keeps logging in with it, until that link is confirmed with "
            + "POST /api/auth/email/verify. A newer request replaces the pending one, and an abandoned "
            + "request just expires. Proposing the address the user already has changes nothing, sends no "
            + "email, and returns 204.",
        responses = {
            @ApiResponse(responseCode = "202", description = "Confirmation email sent to the proposed address"),
            @ApiResponse(responseCode = "204", description = "Proposed address is the one the user already has"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid email address",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "User not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "Email is already registered to another user",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "429",
                description = "Too many email-change requests",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "502",
                description = "The confirmation email could not be sent",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<EmailChangeResponse> requestEmailChange(
        @RequestBody @Valid ChangeEmailRequest req,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        EmailChangeResponse res = userService.requestEmailChange(userId, req);

        if (res == null)
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(res);
    }

}

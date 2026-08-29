package com.synapse.backend.streak;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.streak.dto.StreakResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/user")
@SecurityRequirement(name = "bearerAuth")
public class StreakController {
    private final StreakService streakService;

    public StreakController(StreakService streakService) {
        this.streakService = streakService;
    }

    @GetMapping("/streak")
    @Operation(
        summary = "Get study streak",
        description = "Gets the study streak of the currently authenticated user. "
            + "Streak days are UTC calendar days on which a qualifying study activity was completed.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Successful streak retrieval",
                content = @Content(schema = @Schema(implementation = StreakResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            )
        }
    )
    public ResponseEntity<StreakResponse> getStreak(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        StreakResponse res = streakService.getStreak(userId);

        return ResponseEntity.ok(res);
    }

}

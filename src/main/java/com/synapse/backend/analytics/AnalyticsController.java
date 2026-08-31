package com.synapse.backend.analytics;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.analytics.dto.AnalyticsResponse;

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
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/analytics")
    @Operation(
        summary = "Get study analytics",
        description = "Gets the study analytics of the currently authenticated user over a window of whole "
            + "calendar days ending today. The period is one of 7, 30, 90, or 365 days and defaults to 30. "
            + "Days are counted in the user's own time zone, so the window and the daily grouping follow "
            + "their calendar; stored timestamps remain UTC.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Successful analytics retrieval",
                content = @Content(schema = @Schema(implementation = AnalyticsResponse.class))
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Period is not one of 7, 30, 90, or 365",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            )
        }
    )
    public ResponseEntity<AnalyticsResponse> getAnalytics(
        @RequestParam(defaultValue = "30") int period,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        AnalyticsResponse res = analyticsService.getAnalytics(userId, period);

        return ResponseEntity.ok(res);
    }

}

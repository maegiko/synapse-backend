package com.synapse.backend.flashcards;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.flashcards.dto.FlashcardGenerateListResponse;
import com.synapse.backend.flashcards.dto.FlashcardGenerateNoteRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/api/flashcards")
@SecurityRequirement(name = "bearerAuth")
public class FlashcardController {
    private final FlashcardService flashcardService;

    public FlashcardController(FlashcardService flashcardService) {
        this.flashcardService = flashcardService;
    }

    @PostMapping("/generate")
    @Operation(
        summary = "Generate flashcards from a note",
        description = "Generates flashcards from a saved note owned by the currently authenticated user. "
            + "Saves the generated flashcard deck and flashcards to the database.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful flashcard generation"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid flashcard generation request",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Note not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "502",
                description = "LLM provider error or invalid LLM response",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<FlashcardGenerateListResponse> generateFlashcards(
            @Valid @RequestBody FlashcardGenerateNoteRequest request, @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        FlashcardGenerateListResponse res = flashcardService.generateFlashCards(request.noteId(), userId);

        return ResponseEntity.ok(res);
    }

}

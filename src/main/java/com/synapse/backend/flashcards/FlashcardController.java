package com.synapse.backend.flashcards;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.flashcards.dto.generate.FlashcardGenerateNoteRequest;
import com.synapse.backend.flashcards.dto.generate.FlashcardGenerateResponse;
import com.synapse.backend.flashcards.dto.list.FlashcardListResponse;
import com.synapse.backend.flashcards.dto.list.SingleDeckResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/flashcards")
@SecurityRequirement(name = "bearerAuth")
public class FlashcardController {
    private final FlashcardService flashcardService;
    private final FlashcardPersistenceService persistenceService;

    public FlashcardController(FlashcardService flashcardService, FlashcardPersistenceService persistenceService) {
        this.flashcardService = flashcardService;
        this.persistenceService = persistenceService;
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
    public ResponseEntity<FlashcardGenerateResponse> generateFlashcards(
            @Valid @RequestBody FlashcardGenerateNoteRequest request, @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        FlashcardGenerateResponse res = flashcardService.generateFlashCards(request.noteId(), userId);

        return ResponseEntity.ok(res);
    }

    @GetMapping("/list")
    @Operation(
        summary = "List flashcards",
        description = "Lists all saved flashcard decks and flashcards owned by the currently authenticated user.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful flashcard list retrieval"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            )
        }
    )
    public ResponseEntity<FlashcardListResponse> getAllFlashcards(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        FlashcardListResponse res = persistenceService.getAllFlashcards(userId);

        return ResponseEntity.ok(res);
    }

    @GetMapping("{deckId}")
    @Operation(
        summary = "Get a single flashcard deck",
        description = "Gets a single flashcard deck owned by the currently authenticated user.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful retrieval of flashcard deck"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Flashcard deck not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<SingleDeckResponse> getSingleFlashcardDeck(
        @PathVariable UUID deckId, @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        SingleDeckResponse res = persistenceService.getSingleFlashcardDeck(deckId, userId);

        return ResponseEntity.ok(res);
    }

    @DeleteMapping("{deckId}")
    @Operation(
        summary = "Delete a flashcard deck",
        description = "Deletes a flashcard deck owned by the currently authenticated user.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Successful deletion of flashcard deck"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Flashcard deck not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> deleteDeck(@PathVariable UUID deckId, @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        persistenceService.deleteDeck(deckId, userId);

        return ResponseEntity.noContent().build();
    }

}

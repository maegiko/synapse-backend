package com.synapse.backend.flashcards;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.flashcards.dto.AddFlashcardRequest;
import com.synapse.backend.flashcards.dto.AddFlashcardResponse;
import com.synapse.backend.flashcards.dto.UpdateDeckRequest;
import com.synapse.backend.flashcards.dto.UpdateFlashcardRequest;
import com.synapse.backend.flashcards.dto.generate.FlashcardGenerateNoteRequest;
import com.synapse.backend.flashcards.dto.generate.FlashcardGenerateResponse;
import com.synapse.backend.flashcards.dto.list.FlashcardListResponse;
import com.synapse.backend.flashcards.dto.list.SingleDeckResponse;
import com.synapse.backend.flashcards.dto.review.ReviewDeckRequest;
import com.synapse.backend.flashcards.dto.review.ReviewDeckResponse;
import com.synapse.backend.flashcards.dto.review.ReviewQueueResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
                responseCode = "429",
                description = "Too many generation requests",
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
        description = "Lists saved flashcard decks and flashcards owned by the currently authenticated user, "
            + "newest first. An optional query filters decks by a case-insensitive partial title match; it is "
            + "trimmed and a blank query is ignored. Results are paged with a zero-based page (default 0) and "
            + "a size between 1 and 100 (default 20).",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful flashcard list retrieval"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid page or size",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            )
        }
    )
    public ResponseEntity<FlashcardListResponse> getAllFlashcards(
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        FlashcardListResponse res = persistenceService.getAllFlashcards(userId, query, PageRequest.of(page, size));

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
        @PathVariable String deckId, @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        SingleDeckResponse res = persistenceService.getSingleFlashcardDeck(deckId, userId);

        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/{deckId}")
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
    public ResponseEntity<Void> deleteDeck(@PathVariable String deckId, @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        persistenceService.deleteDeck(deckId, userId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{deckId}")
    @Operation(
        summary = "Update a flashcard deck",
        description = "Updates the title of a flashcard deck owned by the currently authenticated user.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful flashcard deck update"),
            @ApiResponse(
                responseCode = "400",
                description = "Title is missing or blank",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
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
    public ResponseEntity<SingleDeckResponse> updateDeck(
        @PathVariable String deckId,
        @RequestBody @Valid UpdateDeckRequest req,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        SingleDeckResponse res = persistenceService.updateDeck(deckId, userId, req.title());

        return ResponseEntity.ok(res);
    }

    @PostMapping("/{deckId}")
    @Operation(
        summary = "Add a flashcard to a deck",
        description = "Adds a new flashcard to a saved deck owned by the currently authenticated user.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful flashcard creation"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid flashcard request",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
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
    public ResponseEntity<AddFlashcardResponse> addFlashcard(
        @PathVariable String deckId,
        @RequestBody @Valid AddFlashcardRequest req,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        AddFlashcardResponse res = flashcardService.addFlashcard(deckId, userId, req);

        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/{deckId}/cards/{cardId}")
    @Operation(
        summary = "Delete a flashcard from a deck",
        description = "Deletes a flashcard from a saved deck owned by the currently authenticated user.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Successful deletion of flashcard"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Flashcard deck or flashcard not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> deleteFlashcard(
        @PathVariable String deckId,
        @PathVariable String cardId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        flashcardService.deleteFlashcard(userId, deckId, cardId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{deckId}/cards/{cardId}")
    @Operation(
        summary = "Update a flashcard in a deck",
        description = "Updates the question and/or answer of a flashcard in a deck owned by the currently "
            + "authenticated user. Only the supplied fields are changed and at least one must be supplied. "
            + "The parent deck's modified timestamp is advanced.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful flashcard update"),
            @ApiResponse(
                responseCode = "400",
                description = "No field supplied, or a supplied field is blank",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Flashcard deck or flashcard not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<AddFlashcardResponse> updateFlashcard(
        @PathVariable String deckId,
        @PathVariable String cardId,
        @RequestBody @Valid UpdateFlashcardRequest req,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        AddFlashcardResponse res = flashcardService.updateFlashcard(deckId, userId, cardId, req);

        return ResponseEntity.ok(res);
    }

    @GetMapping("/review")
    @Operation(
        summary = "List flashcard decks due for review",
        description = "Lists the flashcard decks owned by the currently authenticated user whose next review "
            + "date is today or earlier, ordered oldest due date first. New decks are due immediately. "
            + "Cards are still retrieved through the single deck endpoint.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful review queue retrieval"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            )
        }
    )
    public ResponseEntity<ReviewQueueResponse> getReviewQueue(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        ReviewQueueResponse res = persistenceService.getReviewQueue(userId);

        return ResponseEntity.ok(res);
    }

    @PostMapping("/{deckId}/review")
    @Operation(
        summary = "Review a flashcard deck",
        description = "Records a review of a flashcard deck owned by the currently authenticated user. "
            + "Reschedules the deck from the supplied rating, saves the review to the deck's history, adds the "
            + "deck's card count to the user's lifetime cards-reviewed total, and records study activity for "
            + "the current day. An optional durationSeconds records how long the session took and feeds "
            + "study-time analytics.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful flashcard deck review"),
            @ApiResponse(
                responseCode = "400",
                description = "Missing or invalid rating, a duration outside 0 to 21600 seconds, "
                    + "or the deck has no flashcards",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
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
    public ResponseEntity<ReviewDeckResponse> reviewDeck(
        @PathVariable String deckId,
        @RequestBody @Valid ReviewDeckRequest req,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        ReviewDeckResponse res = flashcardService.reviewDeck(
            deckId,
            userId,
            req.rating(),
            req.durationSeconds()
        );

        return ResponseEntity.ok(res);
    }

}

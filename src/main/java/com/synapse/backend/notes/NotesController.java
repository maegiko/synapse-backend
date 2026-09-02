package com.synapse.backend.notes;

import com.synapse.backend.notes.dto.NoteListResponse;
import com.synapse.backend.notes.dto.NoteSummaryResponse;
import com.synapse.backend.notes.dto.UpdateNoteRequest;
import com.synapse.backend.shared.validation.ValidationLimits;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/notes")
@SecurityRequirement(name = "bearerAuth")
public class NotesController {
    private final NotesService notesService;
    private final NotesPersistenceService persistenceService;

    public NotesController(NotesService notesService, NotesPersistenceService persistenceService) {
        this.notesService = notesService;
        this.persistenceService = persistenceService;
    }

    @PostMapping(
        value = "/summarise",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(
        summary = "Summarise and save notes",
        description = "Summarises notes uploaded by user. Saves to Database after successful summary generation.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful note summary"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid, empty, unsupported, or unparsable file",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
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
    public ResponseEntity<NoteSummaryResponse> summariseNotes(
        @RequestParam MultipartFile file,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        NoteSummaryResponse res = notesService.summariseNotes(file, userId);

        return ResponseEntity.ok(res);
    }

    @GetMapping("/list")
    @Operation(
        summary = "Get all note summaries",
        description = "Lists saved note summaries for the currently authenticated user, newest first. "
            + "An optional query filters notes by a case-insensitive partial title match; it is trimmed "
            + "and a blank query is ignored. Results are paged with a zero-based page (default 0) and a "
            + "size between 1 and 100 (default 20).",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful note list retrieval"),
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
    public ResponseEntity<NoteListResponse> getAllNotes(
        @RequestParam(required = false) @Size(max = ValidationLimits.SEARCH_QUERY_MAX) String query,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20")
        @Min(ValidationLimits.PAGE_SIZE_MIN)
        @Max(ValidationLimits.PAGE_SIZE_MAX)
        int size,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        NoteListResponse notes = notesService.getAllNoteSummaries(userId, query, PageRequest.of(page, size));

        return ResponseEntity.ok(notes);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get a single note",
        description = "Gets the details of a single note owned by the currently authenticated user.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful retrieval of note"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Note not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<NoteSummaryResponse> getNote(@PathVariable("id") String noteId, @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        NoteSummaryResponse res = notesService.getNoteSummary(noteId, userId);

        return ResponseEntity.ok(res);
    }

    @PatchMapping("/{id}")
    @Operation(
        summary = "Update a note",
        description = "Updates the title and/or overview of a note owned by the currently authenticated user. "
            + "Only the supplied fields are changed and at least one must be supplied. Structured keypoints, "
            + "concepts, and important terms cannot be edited here.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful note update"),
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
                description = "Note not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<NoteSummaryResponse> updateNote(
        @PathVariable("id") String noteId,
        @RequestBody @Valid UpdateNoteRequest req,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        NoteSummaryResponse res = notesService.updateNote(noteId, userId, req);

        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete a note",
        description = "Deletes a note owned by the currently authenticated user.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Successful deletion of note"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Note not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> deleteNote(@PathVariable("id") String noteId, @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        persistenceService.deleteNote(noteId, userId);

        return ResponseEntity.noContent().build();
    }

}

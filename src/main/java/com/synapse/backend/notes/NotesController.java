package com.synapse.backend.notes;

import com.synapse.backend.notes.dto.NoteListResponse;
import com.synapse.backend.notes.dto.NoteSummaryResponse;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
        description = "Lists all saved note summaries for the currently authenticated user.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful note list retrieval"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            )
        }
    )
    public ResponseEntity<NoteListResponse> getAllNotes(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        NoteListResponse notes = notesService.getAllNoteSummaries(userId);

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

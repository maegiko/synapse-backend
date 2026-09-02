package com.synapse.backend.groups;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.groups.dto.CreateGroupRequest;
import com.synapse.backend.groups.dto.GroupDetailResponse;
import com.synapse.backend.groups.dto.GroupListResponse;
import com.synapse.backend.groups.dto.GroupResponse;
import com.synapse.backend.groups.dto.UpdateGroupRequest;
import com.synapse.backend.shared.validation.ValidationLimits;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/groups")
@SecurityRequirement(name = "bearerAuth")
public class GroupController {
    private final GroupService groupService;
    private final GroupPersistenceService persistenceService;

    public GroupController(GroupService groupService, GroupPersistenceService persistenceService) {
        this.groupService = groupService;
        this.persistenceService = persistenceService;
    }

    @PostMapping
    @Operation(
        summary = "Create a study group",
        description = "Creates an empty study group owned by the currently authenticated user. "
            + "Notes, flashcard decks, and quizzes are added to it with the membership routes.",
        responses = {
            @ApiResponse(responseCode = "201", description = "Successful group creation"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid group request",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            )
        }
    )
    public ResponseEntity<GroupResponse> createGroup(
        @RequestBody @Valid CreateGroupRequest req,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        GroupResponse res = groupService.createGroup(userId, req);

        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping("/list")
    @Operation(
        summary = "List study groups",
        description = "Lists study groups owned by the currently authenticated user, newest first, "
            + "with the number of notes, flashcard decks, and quizzes each one holds. An optional query "
            + "filters groups by a case-insensitive partial name match; it is trimmed and a blank query is "
            + "ignored. Results are paged with a zero-based page (default 0) and a size between 1 and 100 "
            + "(default 20).",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful group list retrieval"),
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
    public ResponseEntity<GroupListResponse> getAllGroups(
        @RequestParam(required = false) @Size(max = ValidationLimits.SEARCH_QUERY_MAX) String query,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20")
        @Min(ValidationLimits.PAGE_SIZE_MIN)
        @Max(ValidationLimits.PAGE_SIZE_MAX)
        int size,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        GroupListResponse res = groupService.getAllGroups(userId, query, PageRequest.of(page, size));

        return ResponseEntity.ok(res);
    }

    @GetMapping("/{groupId}")
    @Operation(
        summary = "Get a single study group",
        description = "Gets a study group owned by the currently authenticated user with the notes, "
            + "flashcard decks, and quizzes it holds.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful group retrieval"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Group not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<GroupDetailResponse> getGroup(
        @PathVariable String groupId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        GroupDetailResponse res = groupService.getGroup(groupId, userId);

        return ResponseEntity.ok(res);
    }

    @PatchMapping("/{groupId}")
    @Operation(
        summary = "Update a study group",
        description = "Updates the name and/or description of a study group owned by the currently "
            + "authenticated user. Only the supplied fields are changed and at least one must be supplied.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful group update"),
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
                description = "Group not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<GroupResponse> updateGroup(
        @PathVariable String groupId,
        @RequestBody @Valid UpdateGroupRequest req,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        GroupResponse res = groupService.updateGroup(groupId, userId, req);

        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/{groupId}")
    @Operation(
        summary = "Delete a study group",
        description = "Deletes a study group owned by the currently authenticated user. The notes, "
            + "flashcard decks, and quizzes it held are kept and become ungrouped.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Successful group deletion"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Group not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> deleteGroup(@PathVariable String groupId, @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        groupService.deleteGroup(groupId, userId);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{groupId}/notes/{noteId}")
    @Operation(
        summary = "Add a note to a study group",
        description = "Adds a note owned by the currently authenticated user to a group owned by the same "
            + "user. A note that is already in another group is moved to this one.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Note added to the group"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Group or note not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> addNote(
        @PathVariable String groupId,
        @PathVariable String noteId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        persistenceService.addNote(groupId, noteId, userId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{groupId}/notes/{noteId}")
    @Operation(
        summary = "Remove a note from a study group",
        description = "Removes a note from a group owned by the currently authenticated user. The note "
            + "itself is kept and becomes ungrouped.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Note removed from the group"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Group not found, or the note is not in that group",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> removeNote(
        @PathVariable String groupId,
        @PathVariable String noteId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        persistenceService.removeNote(groupId, noteId, userId);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{groupId}/decks/{deckId}")
    @Operation(
        summary = "Add a flashcard deck to a study group",
        description = "Adds a flashcard deck owned by the currently authenticated user to a group owned by "
            + "the same user. A deck that is already in another group is moved to this one.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Deck added to the group"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Group or flashcard deck not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> addDeck(
        @PathVariable String groupId,
        @PathVariable String deckId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        persistenceService.addDeck(groupId, deckId, userId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{groupId}/decks/{deckId}")
    @Operation(
        summary = "Remove a flashcard deck from a study group",
        description = "Removes a flashcard deck from a group owned by the currently authenticated user. "
            + "The deck itself is kept and becomes ungrouped.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Deck removed from the group"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Group not found, or the deck is not in that group",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> removeDeck(
        @PathVariable String groupId,
        @PathVariable String deckId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        persistenceService.removeDeck(groupId, deckId, userId);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{groupId}/quizzes/{quizId}")
    @Operation(
        summary = "Add a quiz to a study group",
        description = "Adds a quiz owned by the currently authenticated user to a group owned by the same "
            + "user. A quiz that is already in another group is moved to this one.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Quiz added to the group"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Group or quiz not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> addQuiz(
        @PathVariable String groupId,
        @PathVariable String quizId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        persistenceService.addQuiz(groupId, quizId, userId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{groupId}/quizzes/{quizId}")
    @Operation(
        summary = "Remove a quiz from a study group",
        description = "Removes a quiz from a group owned by the currently authenticated user. The quiz "
            + "itself is kept and becomes ungrouped.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Quiz removed from the group"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Group not found, or the quiz is not in that group",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> removeQuiz(
        @PathVariable String groupId,
        @PathVariable String quizId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        persistenceService.removeQuiz(groupId, quizId, userId);

        return ResponseEntity.noContent().build();
    }

}

package com.synapse.backend.notes;

import com.synapse.backend.notes.dto.NoteSummaryResponse;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping("/api/notes")
@SecurityRequirement(name = "bearerAuth")
public class NotesController {
    private final NotesService notesService;

    public NotesController(NotesService notesService) {
        this.notesService = notesService;
    }

    @PostMapping(
        value = "/summarise",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<NoteSummaryResponse> summariseNotes(
        @RequestParam MultipartFile file,
        @AuthenticationPrincipal Jwt jwt
    ) {
        NoteSummaryResponse res = notesService.summariseNotes(file);

        return ResponseEntity.ok(res);
    }

}

package com.synapse.backend.flashcards;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.flashcards.dto.FlashcardGenerateNoteRequest;
import com.synapse.backend.flashcards.dto.FlashcardGenerateResponse;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import java.util.List;

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
    public ResponseEntity<List<FlashcardGenerateResponse>> generateFlashcards(
            @Valid @RequestBody FlashcardGenerateNoteRequest request, @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        List<FlashcardGenerateResponse> res = flashcardService.generateFlashCards(request.noteId(), userId);

        return ResponseEntity.ok(res);
    }

}

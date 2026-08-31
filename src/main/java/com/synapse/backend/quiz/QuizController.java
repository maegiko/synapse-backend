package com.synapse.backend.quiz;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.backend.quiz.dto.GenerateQuizRequest;
import com.synapse.backend.quiz.dto.QuizResponse;
import com.synapse.backend.quiz.dto.UpdateQuestionRequest;
import com.synapse.backend.quiz.dto.UpdateQuizRequest;
import com.synapse.backend.quiz.dto.create.CreateQuestionRequest;
import com.synapse.backend.quiz.dto.create.CreateQuestionResponse;
import com.synapse.backend.quiz.dto.difficulty.UpdateDifficultyRequest;
import com.synapse.backend.quiz.dto.list.ListQuizResponse;
import com.synapse.backend.quiz.dto.score.ListQuizScoreResponse;
import com.synapse.backend.quiz.dto.score.QuizScoreResponse;
import com.synapse.backend.quiz.dto.score.SaveScoreRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/quiz")
@SecurityRequirement(name = "bearerAuth")
public class QuizController {
    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/generate")
    @Operation(
        summary = "Generate a quiz from a note",
        description = "Generates a quiz from a saved note owned by the currently authenticated user. "
            + "Saves the generated quiz, questions, and answers to the database.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful quiz generation"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid quiz generation request",
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
    public ResponseEntity<QuizResponse> generateQuizFromNote(
        @RequestBody @Valid GenerateQuizRequest req,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        QuizResponse res = quizService.generateQuizFromNote(req.noteId(), userId);

        return ResponseEntity.ok(res);
    }

    @GetMapping("/list")
    @Operation(
        summary = "List quizzes",
        description = "Lists saved quizzes owned by the currently authenticated user, newest first, including "
            + "question previews. An optional query filters quizzes by a case-insensitive partial title match; "
            + "it is trimmed and a blank query is ignored. Results are paged with a zero-based page (default 0) "
            + "and a size between 1 and 100 (default 20).",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful quiz list retrieval"),
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
    public ResponseEntity<ListQuizResponse> getAllQuizzes(
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        ListQuizResponse res = quizService.getAllQuizzes(userId, query, PageRequest.of(page, size));

        return ResponseEntity.ok(res);
    }

    @GetMapping("/{quizId}")
    @Operation(
        summary = "Get a single quiz",
        description = "Gets a single saved quiz owned by the currently authenticated user, including questions and answers.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful quiz retrieval"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Quiz not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<QuizResponse> getQuizById(@PathVariable String quizId, @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        QuizResponse res = quizService.getQuizById(quizId, userId);

        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/{quizId}")
    @Operation(
        summary = "Delete a quiz",
        description = "Deletes a saved quiz owned by the currently authenticated user, including its questions and answers.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Successful quiz deletion"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Quiz not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> deleteQuizById(@PathVariable String quizId, @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        quizService.deleteQuizById(quizId, userId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{quizId}")
    @Operation(
        summary = "Update a quiz",
        description = "Updates the title and/or description of a saved quiz owned by the currently "
            + "authenticated user. Only the supplied fields are changed and at least one must be supplied. "
            + "A blank description is stored as null. The difficulty endpoint is unaffected.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Successful quiz update",
                content = @Content(schema = @Schema(implementation = QuizResponse.class))
            ),
            @ApiResponse(
                responseCode = "400",
                description = "No field supplied, or the supplied title is blank",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Quiz not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<QuizResponse> updateQuiz(
        @PathVariable String quizId,
        @RequestBody @Valid UpdateQuizRequest req,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        QuizResponse res = quizService.updateQuiz(userId, quizId, req);

        return ResponseEntity.ok(res);
    }

    @PostMapping("/{quizId}/questions")
    @Operation(
        summary = "Create a quiz question",
        description = "Adds a manually-created question and its answers to a saved quiz owned by the currently "
            + "authenticated user.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Successful question creation",
                content = @Content(schema = @Schema(implementation = CreateQuestionResponse.class))
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid question creation request",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Quiz not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<CreateQuestionResponse> createQuestion(
        @PathVariable String quizId,
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody @Valid CreateQuestionRequest req
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        CreateQuestionResponse res = quizService.createQuestion(userId, quizId, req);

        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/{quizId}/questions/{questionId}")
    @Operation(
        summary = "Delete a quiz question",
        description = "Deletes a question from a saved quiz owned by the currently authenticated user. "
            + "Associated answers are deleted with the question.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Successful question deletion"),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Quiz or question not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> deleteQuestion(
        @PathVariable String quizId,
        @PathVariable String questionId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        quizService.deleteQuestion(userId, quizId, questionId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{quizId}/questions/{questionId}")
    @Operation(
        summary = "Update a quiz question",
        description = "Updates the text, type, and/or answer list of a question in a saved quiz owned by "
            + "the currently authenticated user. Only the supplied fields are changed and at least one must "
            + "be supplied. When answers are supplied they replace the question's whole answer set, and the "
            + "resulting question is validated against the manual question creation rules. The question and "
            + "answer changes are applied atomically and the parent quiz modified timestamp is advanced.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Successful question update",
                content = @Content(schema = @Schema(implementation = CreateQuestionResponse.class))
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid question update request",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Quiz or question not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<CreateQuestionResponse> updateQuestion(
        @PathVariable String quizId,
        @PathVariable String questionId,
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody @Valid UpdateQuestionRequest req
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        CreateQuestionResponse res = quizService.updateQuestion(userId, quizId, questionId, req);

        return ResponseEntity.ok(res);
    }

    @PutMapping("/{quizId}/difficulty")
    @Operation(
        summary = "Update quiz difficulty",
        description = "Sets the difficulty of a saved quiz owned by the currently authenticated user. "
            + "Difficulty must be an integer from 1 to 5.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Successful difficulty update"),
            @ApiResponse(
                responseCode = "400",
                description = "Difficulty is missing or outside the allowed range",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Quiz not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Void> updateDifficulty(
        @PathVariable String quizId,
        @RequestBody @Valid UpdateDifficultyRequest req,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        quizService.updateDifficulty(userId, quizId, req.difficulty());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{quizId}/score")
    @Operation(
        summary = "Save a quiz score",
        description = "Saves a completed quiz score for a quiz owned by the currently authenticated user. "
            + "The score must be between zero and the quiz question count. An optional durationSeconds "
            + "records how long the attempt took and feeds study-time analytics.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Successful score creation",
                content = @Content(schema = @Schema(implementation = QuizScoreResponse.class))
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Score is missing, negative, or greater than the quiz question count, or the "
                    + "duration is outside 0 to 21600 seconds",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Quiz not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<QuizScoreResponse> saveScore(
        @RequestBody @Valid SaveScoreRequest req,
        @PathVariable String quizId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        QuizScoreResponse res = quizService.saveScore(quizId, userId, req.score(), req.durationSeconds());

        return ResponseEntity.ok(res);
    }

    @GetMapping("/{quizId}/score/list")
    @Operation(
        summary = "List quiz scores",
        description = "Lists saved score attempts for a quiz owned by the currently authenticated user, "
            + "ordered from newest to oldest.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Successful quiz score list retrieval",
                content = @Content(schema = @Schema(implementation = ListQuizScoreResponse.class))
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Unauthenticated user",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Quiz not found",
                content = @Content(schema = @Schema(implementation = com.synapse.backend.shared.ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<ListQuizScoreResponse> getAllQuizScores(
        @PathVariable String quizId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        ListQuizScoreResponse res = quizService.getAllQuizScores(quizId, userId);

        return ResponseEntity.ok(res);
    }

}

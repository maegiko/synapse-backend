package com.synapse.backend.notes;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.ai.exceptions.LLMResponseParsingException;
import com.synapse.backend.ai.prompts.NoteSummaryPromptFactory;
import com.synapse.backend.notes.dto.NoteForGeneration;
import com.synapse.backend.notes.dto.NoteListResponse;
import com.synapse.backend.notes.dto.NoteSummaryResponse;
import com.synapse.backend.notes.dto.UpdateNoteRequest;
import com.synapse.backend.notes.exceptions.InvalidFileException;
import com.synapse.backend.notes.exceptions.InvalidNoteDetailsException;
import com.synapse.backend.shared.files.FileParsingService;
import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;
import com.synapse.backend.streak.StreakService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class NotesService {
    private final FileParsingService fileParsingService;
    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;
    private final NoteSummaryPromptFactory promptFactory;
    private final NotesPersistenceService notesPersistenceService;
    private final StreakService streakService;

    public NotesService(
        FileParsingService fileParsingService,
        LLMClient llmClient,
        ObjectMapper objectMapper,
        NoteSummaryPromptFactory promptFactory,
        NotesPersistenceService notesPersistenceService,
        StreakService streakService
    ) {
        this.fileParsingService = fileParsingService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.promptFactory = promptFactory;
        this.notesPersistenceService = notesPersistenceService;
        this.streakService = streakService;
    }

    /**
     * Returns and saves the summarised notes.
     *
     * <p>Study activity is recorded once the summary has been saved.</p>
     *
     * @param file the file to be summarised.
     * @param userId the ID of the user.
     * @return a structured summary of the file.
     * @throws InvalidFileException if the file is missing or empty.
     */
    public NoteSummaryResponse summariseNotes(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty())
            throw new InvalidFileException("The file cannot be empty.");

        String extractedText = fileParsingService.extractText(file);
        NoteSummaryResponse res = generateSummary(extractedText);

        NoteSummaryResponse savedNote = notesPersistenceService.saveNoteSummary(res, userId);
        streakService.recordActivity(userId);

        return savedNote;
    }

    /**
     * Generates a summary of notes.
     *
     * @param extractedText the extracted text from a file.
     * @return the structured summary of the notes.
     * @throws LLMResponseParsingException if the LLM response cannot be parsed.
     */
    private NoteSummaryResponse generateSummary(String extractedText) {
        LLMRequest req = new LLMRequest(
            "openai/gpt-oss-120b",
            promptFactory.createNoteSummarySystemPrompt(),
            promptFactory.createNoteSummaryUserPrompt(extractedText)
        );
        String res = llmClient.generate(req);

        try {
            return withinLimits(objectMapper.readValue(res, NoteSummaryResponse.class));
        } catch (JacksonException e) {
            throw new LLMResponseParsingException("Failed to parse LLM response");
        }
    }

    /**
     * Clamps a generated summary to the bounds the note edit endpoint enforces.
     *
     * <p>Nothing stops a model returning a title or overview longer than PATCH
     * /api/notes/{id} accepts. Were that stored as generated, the user would own a note they
     * could not save again without first shortening text they never wrote.</p>
     *
     * @param summary the parsed summary.
     * @return the same summary with its editable text bounded.
     */
    private NoteSummaryResponse withinLimits(NoteSummaryResponse summary) {
        return new NoteSummaryResponse(
            summary.id(),
            RequestText.clamped(summary.title(), ValidationLimits.TITLE_MAX),
            RequestText.clamped(summary.overview(), ValidationLimits.OVERVIEW_MAX),
            summary.keypoints(),
            summary.concepts(),
            summary.importantTerms(),
            summary.groupId(),
            summary.pinned()
        );
    }

    /**
     * Returns a page of saved note summaries for user, optionally filtered by title.
     *
     * @param userId the ID of the user.
     * @param query an optional case-insensitive partial title search, or null/blank for no search.
     * @param pageable the page to return.
     * @return the requested page of note summaries with its pagination metadata.
     */
    public NoteListResponse getAllNoteSummaries(Long userId, String query, Pageable pageable) {
        return notesPersistenceService.getAllNoteSummaries(userId, query, pageable);
    }

    /**
     * Returns a single note belonging to a user.
     *
     * @param noteId the note ID of the note to return.
     * @param userId the user ID of the user requesting the note.
     * @return the note belonging to the user of a given noteId.
     */
    public NoteSummaryResponse getNoteSummary(String noteId, Long userId) {
        return notesPersistenceService.getNoteSummary(noteId, userId);
    }

    public NoteForGeneration getNoteForGeneration(String noteId, Long userId) {
        return notesPersistenceService.getNoteForGeneration(noteId, userId);
    }

    /**
     * Updates the title and/or overview of a note owned by the user.
     *
     * <p>Only the supplied fields are changed. The request arrives with its title and overview
     * trimmed. Structured keypoints, concepts, and important terms are not editable here.</p>
     *
     * @param noteId the public id of the note.
     * @param userId the ID of the user.
     * @param req the validated fields to update, with at least one field supplied.
     * @return the updated note summary.
     * @throws InvalidNoteDetailsException if no field is supplied.
     * @throws NoteNotFoundException if the note doesn't exist for this user.
     */
    public NoteSummaryResponse updateNote(String noteId, Long userId, UpdateNoteRequest req) {
        String title = req.title();
        String overview = req.overview();
        Boolean pinned = req.pinned();

        if (title == null && overview == null && pinned == null)
            throw new InvalidNoteDetailsException("At least one of title, overview, or pinned must be supplied.");

        return notesPersistenceService.updateNote(noteId, userId, title, overview, pinned);
    }

}

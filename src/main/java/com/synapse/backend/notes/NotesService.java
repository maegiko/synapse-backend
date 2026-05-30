package com.synapse.backend.notes;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.ai.exceptions.LLMResponseParsingException;
import com.synapse.backend.ai.prompts.NoteSummaryPromptFactory;
import com.synapse.backend.notes.dto.NoteForGeneration;
import com.synapse.backend.notes.dto.NoteListResponse;
import com.synapse.backend.notes.dto.NoteSummaryResponse;
import com.synapse.backend.notes.exceptions.InvalidFileException;
import com.synapse.backend.shared.files.FileParsingService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class NotesService {
    private final FileParsingService fileParsingService;
    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;
    private final NoteSummaryPromptFactory promptFactory;
    private final NotesPersistenceService notesPersistenceService;

    public NotesService(
        FileParsingService fileParsingService,
        LLMClient llmClient,
        ObjectMapper objectMapper,
        NoteSummaryPromptFactory promptFactory,
        NotesPersistenceService notesPersistenceService
    ) {
        this.fileParsingService = fileParsingService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.promptFactory = promptFactory;
        this.notesPersistenceService = notesPersistenceService;
    }

    /**
     * Returns and saves the summarised notes.
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

        notesPersistenceService.saveNoteSummary(res, userId);

        return res;
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
            "meta-llama/llama-4-scout-17b-16e-instruct",
            promptFactory.createNoteSummarySystemPrompt(),
            promptFactory.createNoteSummaryUserPrompt(extractedText)
        );
        String res = llmClient.generate(req);

        try {
            return objectMapper.readValue(res, NoteSummaryResponse.class);
        } catch (JacksonException e) {
            throw new LLMResponseParsingException("Failed to parse LLM response");
        }
    }

    /**
     * Returns all saved note summaries for user.
     *
     * @param userId the ID of the user.
     * @return all saved note summaries for the user.
     */
    public NoteListResponse getAllNoteSummaries(Long userId) {
        List<NoteSummaryResponse> notesList = notesPersistenceService.getAllNoteSummaries(userId);

        return new NoteListResponse(notesList);
    }

    /**
     * Returns a single note belonging to a user.
     *
     * @param noteId the note ID of the note to return.
     * @param userId the user ID of the user requesting the note.
     * @return the note belonging to the user of a given noteId.
     */
    public NoteSummaryResponse getNoteSummary(UUID noteId, Long userId) {
        return notesPersistenceService.getNoteSummary(noteId, userId);
    }

    public NoteForGeneration getNoteForGeneration(UUID noteId, Long userId) {
        return notesPersistenceService.getNoteForGeneration(noteId, userId);
    }

}

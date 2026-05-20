package com.synapse.backend.notes;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.synapse.backend.ai.LLMService;
import com.synapse.backend.notes.dto.NoteSummaryResponse;
import com.synapse.backend.notes.exceptions.InvalidFileException;
import com.synapse.backend.shared.files.FileParsingService;

@Service
public class NotesService {
    private final LLMService llmService;
    private final FileParsingService fileParsingService;

    public NotesService(LLMService llmService, FileParsingService fileParsingService) {
        this.llmService = llmService;
        this.fileParsingService = fileParsingService;
    }

    /**
     * Returns the summarised notes.
     * @param file the file to be summarised.
     * @return a structured summary of the file.
     */
    public NoteSummaryResponse summariseNotes(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new InvalidFileException("The file cannot be empty.");

        String extractedText = fileParsingService.extractText(file);
        return llmService.summariseNotes(extractedText);
    }

}

package com.synapse.backend.notes;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.ai.exceptions.LLMResponseParsingException;
import com.synapse.backend.ai.prompts.NoteSummaryPromptFactory;
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

    public NotesService(
        FileParsingService fileParsingService,
        LLMClient llmClient,
        ObjectMapper objectMapper,
        NoteSummaryPromptFactory promptFactory
    ) {
        this.fileParsingService = fileParsingService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.promptFactory = promptFactory;
    }

    /**
     * Returns the summarised notes.
     *
     * @param file the file to be summarised.
     * @return a structured summary of the file.
     * @throws InvalidFileException if the file is missing or empty.
     * @throws LLMResponseParsingException if the LLM response cannot be parsed.
     */
    public NoteSummaryResponse summariseNotes(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new InvalidFileException("The file cannot be empty.");

        String extractedText = fileParsingService.extractText(file);

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

}

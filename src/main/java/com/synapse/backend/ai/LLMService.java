package com.synapse.backend.ai;

import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.ai.exceptions.LLMResponseParsingException;
import com.synapse.backend.notes.dto.NoteSummaryResponse;

import tools.jackson.databind.ObjectMapper;

@Service
public class LLMService {
    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;

    public LLMService(LLMClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Calls the LLM API and returns a structured output of notes.
     * @param extractedText text extracted from a file.
     * @return structured summary of notes.
     * @throws LLMResponseParsingException if the objectMapper fails to parse the LLM response into an object.
     */
    public NoteSummaryResponse summariseNotes(String extractedText) {
        LLMRequest req = new LLMRequest("gemini-2.5-flash", createNoteSummaryPrompt(extractedText));
        String res = llmClient.generate(req);

        try {
            return objectMapper.readValue(res, NoteSummaryResponse.class);
        } catch (JacksonException e) {
            throw new LLMResponseParsingException("Failed to parse LLM response");
        }
    }

    /**
     * Creates a prompt for the LLM to summarise notes and return JSON structure.
     * @param extractedText the extracted text from a file.
     * @return a string of text in NoteSummaryResponse JSON structure.
     */
    private String createNoteSummaryPrompt(String extractedText) {
        return String.format("""
                You are an educational note summariser.

                Summarise the extracted lecture notes into ONLY valid JSON.

                Do not include markdown.
                Do not include code fences.
                Do not include explanations outside the JSON.
                Do not invent information that is not supported by the notes.

                The JSON must exactly match this structure:

                {
                  "title": "string",
                  "overview": "string",
                  "keypoints": ["string"],
                  "concepts": [
                    {
                      "name": "string",
                      "explanation": "string"
                    }
                  ],
                  "importantTerms": ["string"]
                }

                Rules:
                - "title" should be a short title for the notes.
                - "overview" should be a concise summary of the whole document.
                - "keypoints" should contain the main ideas students should remember.
                - "concepts" should explain important concepts from the notes.
                - "importantTerms" should list important terminology from the notes.
                - If a section has no useful content, return an empty array.
                - All keys must be present.
                - Use the exact key names: title, overview, keypoints, concepts, importantTerms.

                Extracted notes:
                \"\"\"
                %s
                \"\"\"
                """, extractedText);
    }
}

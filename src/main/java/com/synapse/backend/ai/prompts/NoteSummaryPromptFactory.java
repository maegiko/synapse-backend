package com.synapse.backend.ai.prompts;

import org.springframework.stereotype.Component;

@Component
public class NoteSummaryPromptFactory {

    /**
     * Creates a system prompt for summarising notes.
     *
     * @return system prompt for summarising notes.
     */
    public String createNoteSummarySystemPrompt() {
        return """
                You are an educational note summariser.

                    Summarise the extracted lecture notes into ONLY valid JSON.

                    Return raw JSON only.
                    The first character of your response must be {.
                    The last character of your response must be }.
                    Do not wrap the JSON in ``` or ```json.
                    Do not include markdown, comments, explanations, or surrounding text.
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
            """;
    }

    /**
     * Creates a user prompt for the LLM to summarise notes.
     *
     * @param extractedText the extracted text from a file.
     * @return a user prompt for summarising text.
     */
    public String createNoteSummaryUserPrompt(String extractedText) {
        return String.format("""
                Extracted notes:
                \"\"\"
                %s
                \"\"\"
                """, extractedText);
    }

}

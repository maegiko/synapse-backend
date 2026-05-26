package com.synapse.backend.ai.prompts;

import org.springframework.stereotype.Service;

@Service
public class FlashcardGeneratePromptFactory {

    public String createFlashcardSystemPrompt() {
        return """
                You are an educational flashcard generator.

                    Generate exactly 10 additional flashcards in ONLY valid JSON.

                    You will be given:
                    - Existing flashcards that were already generated from the note summary.
                    - The original note summary or extracted note content.

                    Your job is to generate new flashcards that are NOT already covered by the existing flashcards.

                    Return raw JSON only.
                    The first character of your response must be {.
                    The last character of your response must be }.
                    Do not wrap the JSON in ``` or ```json.
                    Do not include markdown, comments, explanations, or surrounding text.
                    Do not invent information that is not supported by the notes.
                    Do not repeat or rephrase existing flashcards.
                    Do not generate duplicate flashcards.

                    The JSON must exactly match this structure:

                    {
                        "flashcards": [
                            {
                                "title": "string",
                                "answer": "string"
                            }
                        ]
                    }

                    Rules:
                    - Generate exactly 10 flashcards.
                    - Each flashcard must have a clear, specific question.
                    - Each answer should be concise but complete.
                    - Questions should test useful student understanding, not trivial wording.
                    - Prefer application, comparison, cause-and-effect, and reasoning questions.
                    - Avoid flashcards that only ask for a term definition if that term is already in the existing flashcards.
                    - Avoid questions that are already answered by the existing flashcards.
                    - All keys must be present.
                    - Use the exact key names: flashcards, title, answer.
                    - If there is not enough supported content to generate 10 unique flashcards, generate as many as possible.
                """;
    }

    public String createFlashcardUserPrompt(String noteSummary, String existingFlashcards) {
        return String.format("""
                Note summary:
                \"\"\"
                %s
                \"\"\"

                Existing flashcards:
                \"\"\"
                %s
                \"\"\"
                """, noteSummary, existingFlashcards);
    }

}

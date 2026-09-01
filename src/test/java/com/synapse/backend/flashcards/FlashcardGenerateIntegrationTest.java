package com.synapse.backend.flashcards;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import com.aventrix.jnanoid.jnanoid.NanoIdUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.flashcards.dto.generate.FlashcardGenerateNoteRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class FlashcardGenerateIntegrationTest extends PostgresIntegrationTest {

    private static final String GENERATE_ENDPOINT = "/api/flashcards/generate";
    private static final String VALID_PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private LLMClient llmClient;

    @BeforeEach
    void resetDatabaseAndMocks() {
        jdbcTemplate.execute("DELETE FROM app_user");
        reset(llmClient);
    }

    @Test
    void generateFlashcardsReturnsGeneratedCardsAndSavesDeckAndCardsToDatabase() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn(validFlashcardJson());
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "Biology notes", "An overview of cells.");
        createConcept(note.id(), 0, "Cell", "The basic unit of life.");

        MvcResult result = mockMvc.perform(post(GENERATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FlashcardGenerateNoteRequest(note.publicId())))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deckId").isString())
            .andExpect(jsonPath("$.pinned").value(false))
            .andExpect(jsonPath("$.flashcards", hasSize(3)))
            .andExpect(jsonPath("$.flashcards[0].title").value("Cell"))
            .andExpect(jsonPath("$.flashcards[0].answer").value("The basic unit of life."))
            .andExpect(jsonPath("$.flashcards[1].title").value("Mitochondria"))
            .andExpect(jsonPath("$.flashcards[1].answer").value("The organelle that releases energy for the cell."))
            .andExpect(jsonPath("$.flashcards[2].title").value("Nucleus"))
            .andExpect(jsonPath("$.flashcards[2].answer").value("The organelle that stores genetic material."))
            .andReturn();

        String responseDeckId = objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("deckId")
            .asString();

        Map<String, Object> deck = jdbcTemplate.queryForMap(
            "SELECT id, public_id, user_id, note_id, title, source_type FROM flashcard_deck WHERE note_id = ?",
            note.id()
        );
        Long deckId = ((Number) deck.get("id")).longValue();

        assertEquals(deck.get("public_id").toString(), responseDeckId);
        assertEquals(10, responseDeckId.length());
        assertEquals(user.id(), deck.get("user_id"));
        assertEquals(note.id(), deck.get("note_id"));
        assertEquals("Biology notes", deck.get("title"));
        assertEquals("NOTE", deck.get("source_type"));
        assertEquals(
            Boolean.FALSE,
            jdbcTemplate.queryForObject("SELECT pinned FROM flashcard_deck WHERE id = ?", Boolean.class, deckId)
        );

        List<Map<String, Object>> flashcards = jdbcTemplate.queryForList(
            "SELECT deck_id, question, answer, position FROM flashcard WHERE deck_id = ? ORDER BY position ASC",
            deckId
        );

        assertEquals(3, flashcards.size());
        assertFlashcard(flashcards.get(0), deckId, "Cell", "The basic unit of life.", 0);
        assertFlashcard(
            flashcards.get(1),
            deckId,
            "Mitochondria",
            "The organelle that releases energy for the cell.",
            1
        );
        assertFlashcard(
            flashcards.get(2),
            deckId,
            "Nucleus",
            "The organelle that stores genetic material.",
            2
        );
    }

    @Test
    void generateFlashcardsReturnsNotFoundAndDoesNotCallLlmWhenNoteBelongsToAnotherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        TestNote otherUsersNote = createNote(otherUser.id(), "Private note", "This should stay private.");

        mockMvc.perform(post(GENERATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FlashcardGenerateNoteRequest(otherUsersNote.publicId())))
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Requested note not found."));

        verifyNoInteractions(llmClient);
        assertNoFlashcardsWereSaved();
    }

    @Test
    void generateFlashcardsReturnsBadGatewayAndDoesNotSaveWhenLlmResponseIsInvalid() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn("not json");
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "Biology notes", "An overview of cells.");

        mockMvc.perform(post(GENERATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FlashcardGenerateNoteRequest(note.publicId())))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.message").value("Failed to parse LLM response"));

        assertNoFlashcardsWereSaved();
    }

    @Test
    void generateFlashcardsReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(post(GENERATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FlashcardGenerateNoteRequest(
                    "notegen001"
                ))))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(llmClient);
        assertNoFlashcardsWereSaved();
    }

    private TestUser register(String fullName, String email) throws Exception {
        String accessToken = registerAndAuthenticate(fullName, email, VALID_PASSWORD);
        Long userId = Long.valueOf(jwtDecoder.decode(accessToken).getSubject());

        return new TestUser(userId, accessToken);
    }

    private TestNote createNote(Long userId, String title, String overview) {
        String publicId = NanoIdUtils.randomNanoId(
            NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
            NanoIdUtils.DEFAULT_ALPHABET,
            10
        );
        Long id = jdbcTemplate.queryForObject(
            """
            INSERT INTO note (user_id, public_id, title, overview)
            VALUES (?, ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            userId,
            publicId,
            title,
            overview
        );

        return new TestNote(id, publicId);
    }

    private void createConcept(Long noteId, int position, String name, String explanation) {
        jdbcTemplate.update(
            "INSERT INTO note_concept (note_id, position, name, explanation) VALUES (?, ?, ?, ?)",
            noteId,
            position,
            name,
            explanation
        );
    }

    private void assertNoFlashcardsWereSaved() {
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flashcard_deck",
            Long.class
        ));
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flashcard",
            Long.class
        ));
    }

    private void assertFlashcard(
        Map<String, Object> flashcard,
        Long deckId,
        String question,
        String answer,
        int position
    ) {
        assertNotNull(flashcard);
        assertEquals(deckId, flashcard.get("deck_id"));
        assertEquals(question, flashcard.get("question"));
        assertEquals(answer, flashcard.get("answer"));
        assertEquals(position, flashcard.get("position"));
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String validFlashcardJson() {
        return """
            {
              "flashcards": [
                {
                  "title": "Mitochondria",
                  "answer": "The organelle that releases energy for the cell."
                },
                {
                  "title": "Nucleus",
                  "answer": "The organelle that stores genetic material."
                }
              ]
            }
            """;
    }

    private record TestUser(
        Long id,
        String accessToken
    ) {}

    private record TestNote(
        Long id,
        String publicId
    ) {}
}

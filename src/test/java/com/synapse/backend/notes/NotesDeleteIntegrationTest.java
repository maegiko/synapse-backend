package com.synapse.backend.notes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;

import org.junit.jupiter.api.AfterEach;
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
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class NotesDeleteIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String NOTE_ENDPOINT = "/api/notes/{id}";
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
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @AfterEach
    void doesNotGenerateSummaries() {
        verifyNoInteractions(llmClient);
    }

    @Test
    void deleteNoteDeletesCurrentUsersNoteAndNoteChildrenButKeepsFlashcards() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "Systems note", "Feedback and stock concepts.", "2026-01-02 09:00:00");
        createKeypoint(note.id(), 0, "Feedback loops drive behaviour.");
        createConcept(note.id(), 0, "Stock", "A quantity measured at one point.");
        createImportantTerm(note.id(), 0, "feedback");
        Long deckId = createDeck(
            user.id(),
            note.id(),
            "deckdlt001",
            "Systems deck"
        );
        createFlashcard(
            deckId,
            "carddlt001",
            "What is feedback?",
            "A closed chain of cause and effect.",
            0
        );

        mockMvc.perform(delete(NOTE_ENDPOINT, note.publicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(NOTE_ENDPOINT, note.publicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Requested note not found."));

        assertEquals(0L, countRows("note", note.id()));
        assertEquals(0L, countRows("note_keypoint", "note_id", note.id()));
        assertEquals(0L, countRows("note_concept", "note_id", note.id()));
        assertEquals(0L, countRows("note_important_term", "note_id", note.id()));
        assertEquals(1L, countRows("flashcard_deck", deckId));
        assertEquals(1L, countRows("flashcard", "deck_id", deckId));
        assertNull(jdbcTemplate.queryForObject(
            "SELECT note_id FROM flashcard_deck WHERE id = ?",
            Long.class,
            deckId
        ));
    }

    @Test
    void deleteNoteDoesNotDeleteNoteOwnedByOtherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        TestNote otherUsersNote = createNote(
            otherUser.id(),
            "Private note",
            "This note should stay private.",
            "2026-01-02 09:00:00"
        );
        createKeypoint(otherUsersNote.id(), 0, "Hidden keypoint.");

        mockMvc.perform(delete(NOTE_ENDPOINT, otherUsersNote.publicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Note could not be found: " + otherUsersNote.publicId()));

        mockMvc.perform(get(NOTE_ENDPOINT, otherUsersNote.publicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(otherUser.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(otherUsersNote.publicId().toString()))
            .andExpect(jsonPath("$.title").value("Private note"));

        assertEquals(1L, countRows("note", otherUsersNote.id()));
        assertEquals(1L, countRows("note_keypoint", "note_id", otherUsersNote.id()));
    }

    @Test
    void deleteNoteReturnsNotFoundWhenNoteDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String missingNoteId = "notedlt001";

        mockMvc.perform(delete(NOTE_ENDPOINT, missingNoteId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Note could not be found: " + missingNoteId));
    }

    @Test
    void deleteNoteReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(delete(NOTE_ENDPOINT, "notedlt002"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteNoteReturnsUnauthorizedWhenTokenIsInvalid() throws Exception {
        mockMvc.perform(delete(NOTE_ENDPOINT, "notedlt003")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }

    private TestUser register(String fullName, String email) throws Exception {
        RegisterRequest request = new RegisterRequest(fullName, email, VALID_PASSWORD);

        MvcResult result = mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        String accessToken = objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asString();
        Long userId = Long.valueOf(jwtDecoder.decode(accessToken).getSubject());

        return new TestUser(userId, accessToken);
    }

    private TestNote createNote(Long userId, String title, String overview, String createdAt) {
        String publicId = NanoIdUtils.randomNanoId(
            NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
            NanoIdUtils.DEFAULT_ALPHABET,
            10
        );
        Long id = jdbcTemplate.queryForObject(
            """
            INSERT INTO note (user_id, public_id, title, overview, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?::timestamp, ?::timestamp)
            RETURNING id
            """,
            Long.class,
            userId,
            publicId,
            title,
            overview,
            createdAt,
            createdAt
        );

        return new TestNote(id, publicId);
    }

    private void createKeypoint(Long noteId, int position, String keypoint) {
        jdbcTemplate.update(
            "INSERT INTO note_keypoint (note_id, position, keypoint) VALUES (?, ?, ?)",
            noteId,
            position,
            keypoint
        );
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

    private void createImportantTerm(Long noteId, int position, String term) {
        jdbcTemplate.update(
            "INSERT INTO note_important_term (note_id, position, term) VALUES (?, ?, ?)",
            noteId,
            position,
            term
        );
    }

    private Long createDeck(Long userId, Long noteId, String publicId, String title) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO flashcard_deck (user_id, note_id, title, source_type, public_id)
            VALUES (?, ?, ?, 'NOTE', ?)
            RETURNING id
            """,
            Long.class,
            userId,
            noteId,
            title,
            publicId
        );
    }

    private void createFlashcard(Long deckId, String publicId, String question, String answer, int position) {
        jdbcTemplate.update(
            """
            INSERT INTO flashcard (deck_id, question, answer, position, public_id)
            VALUES (?, ?, ?, ?, ?)
            """,
            deckId,
            question,
            answer,
            position,
            publicId
        );
    }

    private Long countRows(String table, Long id) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE id = ?",
            Long.class,
            id
        );
    }

    private Long countRows(String table, String foreignKeyColumn, Long foreignKeyValue) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + foreignKeyColumn + " = ?",
            Long.class,
            foreignKeyValue
        );
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
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

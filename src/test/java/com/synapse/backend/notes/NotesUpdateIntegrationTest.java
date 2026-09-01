package com.synapse.backend.notes;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.notes.dto.UpdateNoteRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class NotesUpdateIntegrationTest extends PostgresIntegrationTest {

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
    void updateNoteUpdatesOnlyTitleAndLeavesEverythingElseUnchanged() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "Old title", "The overview.");
        createKeypoint(note.id(), 0, "A keypoint.");
        createConcept(note.id(), 0, "Concept", "Concept explanation.");
        createImportantTerm(note.id(), 0, "term");

        updateNote(user, note.publicId(), new UpdateNoteRequest("New title", null, null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(note.publicId()))
            .andExpect(jsonPath("$.title").value("New title"))
            .andExpect(jsonPath("$.overview").value("The overview."))
            .andExpect(jsonPath("$.pinned").value(false))
            .andExpect(jsonPath("$.keypoints", hasSize(1)))
            .andExpect(jsonPath("$.keypoints[0]").value("A keypoint."))
            .andExpect(jsonPath("$.concepts[0].name").value("Concept"))
            .andExpect(jsonPath("$.importantTerms[0]").value("term"));

        assertEquals("New title", noteColumn(note.id(), "title"));
        assertEquals("The overview.", noteColumn(note.id(), "overview"));
    }

    @Test
    void updateNoteUpdatesOnlyOverview() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "The title", "Old overview.");

        updateNote(user, note.publicId(), new UpdateNoteRequest(null, "New overview.", null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("The title"))
            .andExpect(jsonPath("$.overview").value("New overview."));

        assertEquals("The title", noteColumn(note.id(), "title"));
        assertEquals("New overview.", noteColumn(note.id(), "overview"));
    }

    @Test
    void updateNoteUpdatesBothFieldsAndTrimsThem() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "Old title", "Old overview.");

        updateNote(user, note.publicId(), new UpdateNoteRequest("  New title  ", "  New overview.  ", null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("New title"))
            .andExpect(jsonPath("$.overview").value("New overview."));

        assertEquals("New title", noteColumn(note.id(), "title"));
        assertEquals("New overview.", noteColumn(note.id(), "overview"));
    }

    @Test
    void updateNoteRejectsAnEmptyRequest() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "The title", "The overview.");

        mockMvc.perform(patch(NOTE_ENDPOINT, note.publicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("At least one of title, overview, or pinned must be supplied."));

        assertEquals("The title", noteColumn(note.id(), "title"));
    }

    @Test
    void updateNoteRejectsBlankFields() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "The title", "The overview.");

        updateNote(user, note.publicId(), new UpdateNoteRequest("   ", null, null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("title: must not be blank"));

        updateNote(user, note.publicId(), new UpdateNoteRequest(null, "   ", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("overview: must not be blank"));

        assertEquals("The title", noteColumn(note.id(), "title"));
        assertEquals("The overview.", noteColumn(note.id(), "overview"));
    }

    @Test
    void updateNoteReturnsNotFoundWhenNoteDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        updateNote(user, "noteupd999", new UpdateNoteRequest("New title", null, null))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Requested note not found."));
    }

    @Test
    void updateNoteReturnsNotFoundForAnotherUsersNote() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        TestNote otherUsersNote = createNote(otherUser.id(), "Private title", "Private overview.");

        updateNote(user, otherUsersNote.publicId(), new UpdateNoteRequest("Stolen title", null, null))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Requested note not found."));

        assertEquals("Private title", noteColumn(otherUsersNote.id(), "title"));
        assertEquals("Private overview.", noteColumn(otherUsersNote.id(), "overview"));
    }

    @Test
    void updateNotePinsAndUnpinsANote() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "The title", "The overview.");

        updateNote(user, note.publicId(), new UpdateNoteRequest(null, null, true))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("The title"))
            .andExpect(jsonPath("$.pinned").value(true));

        assertEquals(Boolean.TRUE, notePinned(note.id()));

        updateNote(user, note.publicId(), new UpdateNoteRequest(null, null, false))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pinned").value(false));

        assertEquals(Boolean.FALSE, notePinned(note.id()));
    }

    @Test
    void updateNoteAcceptsARequestWithOnlyThePinFlag() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "The title", "The overview.");

        mockMvc.perform(patch(NOTE_ENDPOINT, note.publicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pinned\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pinned").value(true));

        assertEquals("The title", noteColumn(note.id(), "title"));
        assertEquals(Boolean.TRUE, notePinned(note.id()));
    }

    @Test
    void updateNoteLeavesThePinStateUnchangedWhenItIsNotSupplied() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "The title", "The overview.");

        updateNote(user, note.publicId(), new UpdateNoteRequest(null, null, true))
            .andExpect(status().isOk());

        updateNote(user, note.publicId(), new UpdateNoteRequest("Renamed", null, null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pinned").value(true));

        assertEquals(Boolean.TRUE, notePinned(note.id()));
    }

    @Test
    void updateNoteDoesNotChangeAnotherUsersPinState() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        TestNote otherUsersNote = createNote(otherUser.id(), "Private title", "Private overview.");

        updateNote(user, otherUsersNote.publicId(), new UpdateNoteRequest(null, null, true))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Requested note not found."));

        assertEquals(Boolean.FALSE, notePinned(otherUsersNote.id()));
    }

    @Test
    void updateNoteReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(patch(NOTE_ENDPOINT, "noteupd001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateNoteRequest("New title", null, null))))
            .andExpect(status().isUnauthorized());
    }

    private ResultActions updateNote(TestUser user, String noteId, UpdateNoteRequest req) throws Exception {
        return mockMvc.perform(patch(NOTE_ENDPOINT, noteId)
            .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)));
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

    private String noteColumn(Long noteId, String column) {
        return jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM note WHERE id = ?",
            String.class,
            noteId
        );
    }

    private Boolean notePinned(Long noteId) {
        return jdbcTemplate.queryForObject("SELECT pinned FROM note WHERE id = ?", Boolean.class, noteId);
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

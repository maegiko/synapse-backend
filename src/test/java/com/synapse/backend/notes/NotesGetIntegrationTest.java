package com.synapse.backend.notes;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;

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
class NotesGetIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String NOTES_ENDPOINT = "/api/notes/";
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
    void getNoteReturnsTheRequestedNoteForCurrentUser() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createNote(user.id(), "First note", "This note should not be returned.", "2026-01-01 09:00:00");
        TestNote requestedNote = createNote(
            user.id(),
            "Requested note",
            "This is the requested note.",
            "2026-01-02 09:00:00"
        );
        createKeypoint(requestedNote.id(), 1, "Second keypoint.");
        createKeypoint(requestedNote.id(), 0, "First keypoint.");
        createConcept(requestedNote.id(), 0, "Concept", "Concept explanation.");
        createImportantTerm(requestedNote.id(), 0, "term");

        mockMvc.perform(get(NOTES_ENDPOINT + requestedNote.publicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(requestedNote.publicId().toString()))
            .andExpect(jsonPath("$.title").value("Requested note"))
            .andExpect(jsonPath("$.overview").value("This is the requested note."))
            .andExpect(jsonPath("$.keypoints", hasSize(2)))
            .andExpect(jsonPath("$.keypoints[0]").value("First keypoint."))
            .andExpect(jsonPath("$.keypoints[1]").value("Second keypoint."))
            .andExpect(jsonPath("$.concepts[0].name").value("Concept"))
            .andExpect(jsonPath("$.concepts[0].explanation").value("Concept explanation."))
            .andExpect(jsonPath("$.importantTerms[0]").value("term"))
            .andExpect(jsonPath("$.pinned").value(false));
    }

    @Test
    void getNoteReturnsNotFoundForAnotherUsersNote() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        TestNote otherUsersNote = createNote(
            otherUser.id(),
            "Other user's note",
            "This note must not be visible.",
            "2026-01-01 09:00:00"
        );

        mockMvc.perform(get(NOTES_ENDPOINT + otherUsersNote.publicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Requested note not found."));
    }

    @Test
    void getNoteReturnsNotFoundWhenNoteDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String missingNoteId = "noteget999";

        mockMvc.perform(get(NOTES_ENDPOINT + missingNoteId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Requested note not found."));
    }

    @Test
    void getNoteReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get(NOTES_ENDPOINT + "noteget001"))
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

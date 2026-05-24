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
class NotesListIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String LIST_ENDPOINT = "/api/notes/list";
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
    void listNotesReturnsCurrentUsersSavedSummariesNewestFirst() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createNote(
            user.id(),
            "Older behavioural modelling",
            "This older note has no structured children.",
            "2026-01-01 09:00:00"
        );
        Long newerNoteId = createNote(
            user.id(),
            "Newer system dynamics",
            "Feedback loops and stocks are the main ideas.",
            "2026-01-02 09:00:00"
        );
        createKeypoint(newerNoteId, 1, "Stocks accumulate over time.");
        createKeypoint(newerNoteId, 0, "Feedback loops drive behaviour.");
        createConcept(newerNoteId, 1, "Stock", "A quantity measured at a point in time.");
        createConcept(newerNoteId, 0, "Feedback loop", "A closed chain of cause and effect.");
        createImportantTerm(newerNoteId, 1, "stock");
        createImportantTerm(newerNoteId, 0, "feedback");

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(2)))
            .andExpect(jsonPath("$.notes[0].title").value("Newer system dynamics"))
            .andExpect(jsonPath("$.notes[0].overview").value("Feedback loops and stocks are the main ideas."))
            .andExpect(jsonPath("$.notes[0].keypoints", hasSize(2)))
            .andExpect(jsonPath("$.notes[0].keypoints[0]").value("Feedback loops drive behaviour."))
            .andExpect(jsonPath("$.notes[0].keypoints[1]").value("Stocks accumulate over time."))
            .andExpect(jsonPath("$.notes[0].concepts", hasSize(2)))
            .andExpect(jsonPath("$.notes[0].concepts[0].name").value("Feedback loop"))
            .andExpect(jsonPath("$.notes[0].concepts[0].explanation").value("A closed chain of cause and effect."))
            .andExpect(jsonPath("$.notes[0].concepts[1].name").value("Stock"))
            .andExpect(jsonPath("$.notes[0].concepts[1].explanation").value("A quantity measured at a point in time."))
            .andExpect(jsonPath("$.notes[0].importantTerms", hasSize(2)))
            .andExpect(jsonPath("$.notes[0].importantTerms[0]").value("feedback"))
            .andExpect(jsonPath("$.notes[0].importantTerms[1]").value("stock"))
            .andExpect(jsonPath("$.notes[1].title").value("Older behavioural modelling"))
            .andExpect(jsonPath("$.notes[1].overview").value("This older note has no structured children."))
            .andExpect(jsonPath("$.notes[1].keypoints", hasSize(0)))
            .andExpect(jsonPath("$.notes[1].concepts", hasSize(0)))
            .andExpect(jsonPath("$.notes[1].importantTerms", hasSize(0)));
    }

    @Test
    void listNotesReturnsEmptyArrayWhenUserHasNoNotes() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(0)));
    }

    @Test
    void listNotesDoesNotReturnNotesOwnedByOtherUsers() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        Long visibleNoteId = createNote(
            currentUser.id(),
            "Current user's notes",
            "Only this note should be visible.",
            "2026-01-02 09:00:00"
        );
        createNote(otherUser.id(), "Other user's notes", "This note must not leak.", "2026-01-03 09:00:00");
        createKeypoint(visibleNoteId, 0, "Visible keypoint");

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(1)))
            .andExpect(jsonPath("$.notes[0].title").value("Current user's notes"))
            .andExpect(jsonPath("$.notes[0].overview").value("Only this note should be visible."))
            .andExpect(jsonPath("$.notes[0].keypoints[0]").value("Visible keypoint"));
    }

    @Test
    void listNotesReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get(LIST_ENDPOINT))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listNotesReturnsUnauthorizedWhenTokenIsInvalid() throws Exception {
        mockMvc.perform(get(LIST_ENDPOINT)
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

    private Long createNote(Long userId, String title, String overview, String createdAt) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO note (user_id, title, overview, created_at, updated_at)
            VALUES (?, ?, ?, ?::timestamp, ?::timestamp)
            RETURNING id
            """,
            Long.class,
            userId,
            title,
            overview,
            createdAt,
            createdAt
        );
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
}

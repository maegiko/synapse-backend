package com.synapse.backend.notes;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
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
        TestNote olderNote = createNote(
            user.id(),
            "Older behavioural modelling",
            "This older note has no structured children.",
            "2026-01-01 09:00:00"
        );
        TestNote newerNote = createNote(
            user.id(),
            "Newer system dynamics",
            "Feedback loops and stocks are the main ideas.",
            "2026-01-02 09:00:00"
        );
        createKeypoint(newerNote.id(), 1, "Stocks accumulate over time.");
        createKeypoint(newerNote.id(), 0, "Feedback loops drive behaviour.");
        createConcept(newerNote.id(), 1, "Stock", "A quantity measured at a point in time.");
        createConcept(newerNote.id(), 0, "Feedback loop", "A closed chain of cause and effect.");
        createImportantTerm(newerNote.id(), 1, "stock");
        createImportantTerm(newerNote.id(), 0, "feedback");

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(2)))
            .andExpect(jsonPath("$.notes[0].id").value(newerNote.publicId().toString()))
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
            .andExpect(jsonPath("$.notes[1].id").value(olderNote.publicId().toString()))
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
        TestNote visibleNote = createNote(
            currentUser.id(),
            "Current user's notes",
            "Only this note should be visible.",
            "2026-01-02 09:00:00"
        );
        createNote(otherUser.id(), "Other user's notes", "This note must not leak.", "2026-01-03 09:00:00");
        createKeypoint(visibleNote.id(), 0, "Visible keypoint");

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(1)))
            .andExpect(jsonPath("$.notes[0].id").value(visibleNote.publicId().toString()))
            .andExpect(jsonPath("$.notes[0].title").value("Current user's notes"))
            .andExpect(jsonPath("$.notes[0].overview").value("Only this note should be visible."))
            .andExpect(jsonPath("$.notes[0].keypoints[0]").value("Visible keypoint"));
    }

    @Test
    void listNotesUsesDefaultPaginationWhenNoParametersAreSupplied() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createNotes(user.id(), "Note", 25);

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(20)))
            .andExpect(jsonPath("$.notes[0].title").value("Note 25"))
            .andExpect(jsonPath("$.notes[19].title").value("Note 6"))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(25))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void listNotesReturnsTheRequestedPageAndSize() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createNotes(user.id(), "Note", 5);

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("page", "1")
                .param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(2)))
            .andExpect(jsonPath("$.notes[0].title").value("Note 3"))
            .andExpect(jsonPath("$.notes[1].title").value("Note 2"))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void listNotesReturnsTheLastPageWithoutANextPage() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createNotes(user.id(), "Note", 5);

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("page", "2")
                .param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(1)))
            .andExpect(jsonPath("$.notes[0].title").value("Note 1"))
            .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listNotesReturnsAnEmptyPageBeyondTheEnd() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createNotes(user.id(), "Note", 3);

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("page", "5")
                .param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(0)))
            .andExpect(jsonPath("$.page").value(5))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listNotesSearchesTitlesCaseInsensitivelyAndPartially() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createNote(user.id(), "System Dynamics", "Overview", "2026-01-01 09:00:00");
        createNote(user.id(), "Nervous system", "Overview", "2026-01-02 09:00:00");
        createNote(user.id(), "Enzymes", "Overview", "2026-01-03 09:00:00");

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("query", "SYSTEM")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(2)))
            .andExpect(jsonPath("$.notes[0].title").value("Nervous system"))
            .andExpect(jsonPath("$.notes[1].title").value("System Dynamics"))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listNotesTrimsTheSearchQuery() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createNote(user.id(), "Enzymes", "Overview", "2026-01-01 09:00:00");
        createNote(user.id(), "Cells", "Overview", "2026-01-02 09:00:00");

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("query", "  enzy  ")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(1)))
            .andExpect(jsonPath("$.notes[0].title").value("Enzymes"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listNotesTreatsABlankSearchQueryAsNoSearch() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createNotes(user.id(), "Note", 3);

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("query", "   ")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(3)))
            .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void listNotesReturnsAnEmptyPageWhenTheSearchMatchesNothing() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createNotes(user.id(), "Note", 3);

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("query", "photosynthesis")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(0)))
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.totalPages").value(0))
            .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listNotesBreaksCreatedAtTiesByNewestIdFirst() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote first = createNote(user.id(), "First saved", "Overview", "2026-01-01 09:00:00");
        TestNote second = createNote(user.id(), "Second saved", "Overview", "2026-01-01 09:00:00");
        TestNote third = createNote(user.id(), "Third saved", "Overview", "2026-01-01 09:00:00");

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes[0].id").value(third.publicId()))
            .andExpect(jsonPath("$.notes[1].id").value(second.publicId()))
            .andExpect(jsonPath("$.notes[2].id").value(first.publicId()));

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("page", "1")
                .param("size", "1")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(1)))
            .andExpect(jsonPath("$.notes[0].id").value(second.publicId()));
    }

    @Test
    void listNotesReturnsPinnedNotesBeforeNewerUnpinnedNotes() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote pinned = createNote(user.id(), "Pinned older note", "Overview", "2026-01-01 09:00:00", true);
        createNote(user.id(), "Unpinned newer note", "Overview", "2026-01-05 09:00:00");

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(2)))
            .andExpect(jsonPath("$.notes[0].id").value(pinned.publicId()))
            .andExpect(jsonPath("$.notes[0].pinned").value(true))
            .andExpect(jsonPath("$.notes[1].title").value("Unpinned newer note"))
            .andExpect(jsonPath("$.notes[1].pinned").value(false));
    }

    @Test
    void listNotesOrdersMultiplePinnedNotesByNewestThenIdDescending() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote pinnedNewer = createNote(user.id(), "Pinned newer", "Overview", "2026-01-04 09:00:00", true);
        TestNote pinnedTieFirst = createNote(user.id(), "Pinned tie first", "Overview", "2026-01-02 09:00:00", true);
        TestNote pinnedTieSecond = createNote(user.id(), "Pinned tie second", "Overview", "2026-01-02 09:00:00", true);
        createNote(user.id(), "Unpinned newest", "Overview", "2026-01-09 09:00:00");

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(4)))
            .andExpect(jsonPath("$.notes[0].id").value(pinnedNewer.publicId()))
            .andExpect(jsonPath("$.notes[1].id").value(pinnedTieSecond.publicId()))
            .andExpect(jsonPath("$.notes[2].id").value(pinnedTieFirst.publicId()))
            .andExpect(jsonPath("$.notes[3].title").value("Unpinned newest"));
    }

    @Test
    void listNotesKeepsPinnedFirstWhenSearching() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote pinned = createNote(user.id(), "System dynamics primer", "Overview", "2026-01-01 09:00:00", true);
        createNote(user.id(), "Nervous system newer", "Overview", "2026-01-06 09:00:00");

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("query", "system")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(2)))
            .andExpect(jsonPath("$.notes[0].id").value(pinned.publicId()))
            .andExpect(jsonPath("$.notes[1].title").value("Nervous system newer"));
    }

    @Test
    void listNotesKeepsPinnedFirstAcrossPages() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote pinned = createNote(user.id(), "Pinned note", "Overview", "2026-01-01 09:00:00", true);
        createNote(user.id(), "Unpinned A", "Overview", "2026-01-03 09:00:00");
        createNote(user.id(), "Unpinned B", "Overview", "2026-01-02 09:00:00");

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("page", "0")
                .param("size", "1")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(1)))
            .andExpect(jsonPath("$.notes[0].id").value(pinned.publicId()));

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("page", "1")
                .param("size", "1")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(1)))
            .andExpect(jsonPath("$.notes[0].title").value("Unpinned A"));
    }

    @Test
    void listNotesOnlySearchesNotesOwnedByTheAuthenticatedUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        createNote(currentUser.id(), "Shared title mine", "Overview", "2026-01-01 09:00:00");
        createNote(otherUser.id(), "Shared title theirs", "Overview", "2026-01-02 09:00:00");

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("query", "shared title")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes", hasSize(1)))
            .andExpect(jsonPath("$.notes[0].title").value("Shared title mine"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listNotesRejectsANegativePage() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("page", "-1")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("page: must be greater than or equal to 0"));
    }

    @Test
    void listNotesRejectsASizeBelowOne() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("size", "0")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("size: must be greater than or equal to 1"));
    }

    @Test
    void listNotesRejectsASizeAboveTheMaximum() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("size", "101")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("size: must be less than or equal to 100"));
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

    private TestNote createNote(Long userId, String title, String overview, String createdAt) {
        return createNote(userId, title, overview, createdAt, false);
    }

    private TestNote createNote(Long userId, String title, String overview, String createdAt, boolean pinned) {
        String publicId = NanoIdUtils.randomNanoId(
            NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
            NanoIdUtils.DEFAULT_ALPHABET,
            10
        );
        Long id = jdbcTemplate.queryForObject(
            """
            INSERT INTO note (user_id, public_id, title, overview, created_at, updated_at, pinned)
            VALUES (?, ?, ?, ?, ?::timestamp, ?::timestamp, ?)
            RETURNING id
            """,
            Long.class,
            userId,
            publicId,
            title,
            overview,
            createdAt,
            createdAt,
            pinned
        );

        return new TestNote(id, publicId);
    }

    private void createNotes(Long userId, String titlePrefix, int count) {
        for (int i = 1; i <= count; i++) {
            createNote(
                userId,
                titlePrefix + " " + i,
                "Overview " + i,
                String.format("2026-01-01 09:%02d:00", i)
            );
        }
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

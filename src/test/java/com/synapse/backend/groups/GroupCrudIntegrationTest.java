package com.synapse.backend.groups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.groups.dto.CreateGroupRequest;
import com.synapse.backend.groups.dto.UpdateGroupRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class GroupCrudIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String GROUPS_ENDPOINT = "/api/groups";
    private static final String GROUP_LIST_ENDPOINT = "/api/groups/list";
    private static final String GROUP_ENDPOINT = "/api/groups/{groupId}";
    private static final String GROUP_NOTE_ENDPOINT = "/api/groups/{groupId}/notes/{noteId}";
    private static final String GROUP_DECK_ENDPOINT = "/api/groups/{groupId}/decks/{deckId}";
    private static final String GROUP_QUIZ_ENDPOINT = "/api/groups/{groupId}/quizzes/{quizId}";
    private static final String VALID_PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void createGroupReturnsCreatedGroupAndSavesIt() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        MvcResult result = createGroup(user, new CreateGroupRequest("Systems", "Semester two revision"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Systems"))
            .andExpect(jsonPath("$.description").value("Semester two revision"))
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andReturn();

        String groupId = groupIdOf(result);

        assertEquals(10, groupId.length());
        assertEquals("Systems", jdbcTemplate.queryForObject(
            "SELECT name FROM study_group WHERE public_id = ?", String.class, groupId));
        assertEquals(user.id(), jdbcTemplate.queryForObject(
            "SELECT user_id FROM study_group WHERE public_id = ?", Long.class, groupId));
    }

    @Test
    void createGroupTrimsTheNameAndDescription() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        createGroup(user, new CreateGroupRequest("  Systems  ", "  Semester two  "))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Systems"))
            .andExpect(jsonPath("$.description").value("Semester two"));
    }

    @Test
    void createGroupAllowsAnOmittedDescription() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        createGroup(user, new CreateGroupRequest("Systems", null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.description").isEmpty());
    }

    @Test
    void createGroupStoresABlankDescriptionAsNull() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        createGroup(user, new CreateGroupRequest("Systems", "   "))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.description").isEmpty());
    }

    @Test
    void createGroupRequiresAName() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        createGroup(user, new CreateGroupRequest(null, "Semester two"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("name: must not be blank"));

        createGroup(user, new CreateGroupRequest("   ", "Semester two"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("name: must not be blank"));

        assertEquals(0, countGroups(user.id()));
    }

    @Test
    void createGroupRejectsAnOverlongNameAndDescription() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        createGroup(user, new CreateGroupRequest("N".repeat(101), null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("name: size must be between 0 and 100"));

        createGroup(user, new CreateGroupRequest("Systems", "D".repeat(501)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("description: size must be between 0 and 500"));

        assertEquals(0, countGroups(user.id()));
    }

    @Test
    void createGroupReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(post(GROUPS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateGroupRequest("Systems", null))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listGroupsReturnsTheUsersGroupsNewestFirstWithContentCounts() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String oldest = createGroupId(user, "Oldest");
        String newest = createGroupId(user, "Newest");

        addNote(user, newest, createNote(user.id(), "Cells"));
        addNote(user, newest, createNote(user.id(), "Enzymes"));
        addDeck(user, newest, createDeck(user.id(), "Cell deck"));
        addQuiz(user, newest, createQuiz(user.id(), "Cell quiz"));

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups.length()").value(2))
            .andExpect(jsonPath("$.groups[0].id").value(newest))
            .andExpect(jsonPath("$.groups[0].noteCount").value(2))
            .andExpect(jsonPath("$.groups[0].deckCount").value(1))
            .andExpect(jsonPath("$.groups[0].quizCount").value(1))
            .andExpect(jsonPath("$.groups[1].id").value(oldest))
            .andExpect(jsonPath("$.groups[1].noteCount").value(0))
            .andExpect(jsonPath("$.groups[1].deckCount").value(0))
            .andExpect(jsonPath("$.groups[1].quizCount").value(0));
    }

    @Test
    void listGroupsIsEmptyWhenTheUserHasNoGroups() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups").isEmpty());
    }

    @Test
    void listGroupsOnlyReturnsGroupsOwnedByTheAuthenticatedUser() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        String mine = createGroupId(user, "Mine");
        createGroupId(otherUser, "Theirs");

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups.length()").value(1))
            .andExpect(jsonPath("$.groups[0].id").value(mine));
    }

    @Test
    void listGroupsUsesDefaultPaginationWhenNoParametersAreSupplied() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createGroupRows(user.id(), "Group", 25);

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups.length()").value(20))
            .andExpect(jsonPath("$.groups[0].name").value("Group 25"))
            .andExpect(jsonPath("$.groups[19].name").value("Group 6"))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(25))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void listGroupsReturnsTheRequestedPageAndSize() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createGroupRows(user.id(), "Group", 5);

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .param("page", "1")
                .param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups.length()").value(2))
            .andExpect(jsonPath("$.groups[0].name").value("Group 3"))
            .andExpect(jsonPath("$.groups[1].name").value("Group 2"))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void listGroupsReturnsAnEmptyPageBeyondTheEnd() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createGroupRows(user.id(), "Group", 3);

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .param("page", "5")
                .param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups").isEmpty())
            .andExpect(jsonPath("$.page").value(5))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listGroupsSearchesNamesCaseInsensitivelyAndPartiallyKeepingContentCounts() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String systems = createGroupRow(user.id(), "grpsrc0001", "System Dynamics", "2026-01-01 09:00:00");
        createGroupRow(user.id(), "grpsrc0002", "Nervous system", "2026-01-02 09:00:00");
        createGroupRow(user.id(), "grpsrc0003", "Enzymes", "2026-01-03 09:00:00");

        addNote(user, systems, createNote(user.id(), "Stocks and flows"));

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .param("query", "SYSTEM")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups.length()").value(2))
            .andExpect(jsonPath("$.groups[0].name").value("Nervous system"))
            .andExpect(jsonPath("$.groups[1].name").value("System Dynamics"))
            .andExpect(jsonPath("$.groups[1].noteCount").value(1))
            .andExpect(jsonPath("$.groups[1].deckCount").value(0))
            .andExpect(jsonPath("$.groups[1].quizCount").value(0))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listGroupsTrimsTheSearchQuery() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createGroupRow(user.id(), "grptrm0001", "Enzymes", "2026-01-01 09:00:00");
        createGroupRow(user.id(), "grptrm0002", "Cells", "2026-01-02 09:00:00");

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .param("query", "  enzy  ")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups.length()").value(1))
            .andExpect(jsonPath("$.groups[0].name").value("Enzymes"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listGroupsTreatsABlankSearchQueryAsNoSearch() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createGroupRows(user.id(), "Group", 3);

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .param("query", "   ")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups.length()").value(3))
            .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void listGroupsReturnsAnEmptyPageWhenTheSearchMatchesNothing() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createGroupRows(user.id(), "Group", 3);

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .param("query", "photosynthesis")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.totalPages").value(0))
            .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listGroupsBreaksCreatedAtTiesByNewestIdFirst() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String first = createGroupRow(user.id(), "grptie0001", "First saved", "2026-01-01 09:00:00");
        String second = createGroupRow(user.id(), "grptie0002", "Second saved", "2026-01-01 09:00:00");
        String third = createGroupRow(user.id(), "grptie0003", "Third saved", "2026-01-01 09:00:00");

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].id").value(third))
            .andExpect(jsonPath("$.groups[1].id").value(second))
            .andExpect(jsonPath("$.groups[2].id").value(first));

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .param("page", "1")
                .param("size", "1")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups.length()").value(1))
            .andExpect(jsonPath("$.groups[0].id").value(second));
    }

    @Test
    void listGroupsOnlySearchesGroupsOwnedByTheAuthenticatedUser() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        createGroupRow(user.id(), "grpown0001", "Shared name mine", "2026-01-01 09:00:00");
        createGroupRow(otherUser.id(), "grpown0002", "Shared name theirs", "2026-01-02 09:00:00");

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .param("query", "shared name")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups.length()").value(1))
            .andExpect(jsonPath("$.groups[0].name").value("Shared name mine"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listGroupsRejectsANegativePage() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .param("page", "-1")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("page: must be greater than or equal to 0"));
    }

    @Test
    void listGroupsRejectsASizeBelowOne() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .param("size", "0")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("size: must be greater than or equal to 1"));
    }

    @Test
    void listGroupsRejectsASizeAboveTheMaximum() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)
                .param("size", "101")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("size: must be less than or equal to 100"));
    }

    @Test
    void getGroupReturnsItsMixedContentNewestFirst() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String groupId = createGroupId(user, "Systems");

        String olderNote = createNote(user.id(), "Cells");
        String newerNote = createNote(user.id(), "Enzymes");
        String deckId = createDeck(user.id(), "Cell deck");
        String quizId = createQuiz(user.id(), "Cell quiz");

        addNote(user, groupId, olderNote);
        addNote(user, groupId, newerNote);
        addDeck(user, groupId, deckId);
        addQuiz(user, groupId, quizId);

        mockMvc.perform(get(GROUP_ENDPOINT, groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(groupId))
            .andExpect(jsonPath("$.name").value("Systems"))
            .andExpect(jsonPath("$.notes.length()").value(2))
            .andExpect(jsonPath("$.notes[0].id").value(newerNote))
            .andExpect(jsonPath("$.notes[0].title").value("Enzymes"))
            .andExpect(jsonPath("$.notes[1].id").value(olderNote))
            .andExpect(jsonPath("$.decks.length()").value(1))
            .andExpect(jsonPath("$.decks[0].id").value(deckId))
            .andExpect(jsonPath("$.decks[0].title").value("Cell deck"))
            .andExpect(jsonPath("$.quizzes.length()").value(1))
            .andExpect(jsonPath("$.quizzes[0].id").value(quizId))
            .andExpect(jsonPath("$.quizzes[0].title").value("Cell quiz"));
    }

    @Test
    void getGroupReturnsEmptyContentListsForANewGroup() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String groupId = createGroupId(user, "Systems");

        mockMvc.perform(get(GROUP_ENDPOINT, groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes").isEmpty())
            .andExpect(jsonPath("$.decks").isEmpty())
            .andExpect(jsonPath("$.quizzes").isEmpty());
    }

    @Test
    void getGroupReturnsNotFoundForAnotherUsersGroupAndForAnUnknownId() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        String otherGroup = createGroupId(otherUser, "Theirs");

        mockMvc.perform(get(GROUP_ENDPOINT, otherGroup)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Group not found: " + otherGroup));

        mockMvc.perform(get(GROUP_ENDPOINT, "missing001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Group not found: missing001"));
    }

    @Test
    void updateGroupChangesOnlyTheSuppliedFields() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String groupId = createGroupId(user, "Systems");

        updateGroup(user, groupId, new UpdateGroupRequest("Operating Systems", null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(groupId))
            .andExpect(jsonPath("$.name").value("Operating Systems"))
            .andExpect(jsonPath("$.description").isEmpty());

        updateGroup(user, groupId, new UpdateGroupRequest(null, "Semester two revision"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Operating Systems"))
            .andExpect(jsonPath("$.description").value("Semester two revision"));

        assertEquals("Operating Systems", jdbcTemplate.queryForObject(
            "SELECT name FROM study_group WHERE public_id = ?", String.class, groupId));
        assertEquals("Semester two revision", jdbcTemplate.queryForObject(
            "SELECT description FROM study_group WHERE public_id = ?", String.class, groupId));
    }

    @Test
    void updateGroupClearsTheDescriptionWhenItIsBlank() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String groupId = createGroupId(user, "Systems");

        updateGroup(user, groupId, new UpdateGroupRequest(null, "Semester two"))
            .andExpect(status().isOk());

        updateGroup(user, groupId, new UpdateGroupRequest(null, "  "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").isEmpty());

        assertNull(jdbcTemplate.queryForObject(
            "SELECT description FROM study_group WHERE public_id = ?", String.class, groupId));
    }

    @Test
    void updateGroupRequiresAtLeastOneField() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String groupId = createGroupId(user, "Systems");

        updateGroup(user, groupId, new UpdateGroupRequest(null, null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("At least one of name or description must be supplied."));

        assertEquals("Systems", jdbcTemplate.queryForObject(
            "SELECT name FROM study_group WHERE public_id = ?", String.class, groupId));
    }

    @Test
    void updateGroupRejectsABlankName() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String groupId = createGroupId(user, "Systems");

        updateGroup(user, groupId, new UpdateGroupRequest("   ", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("name: must not be blank"));

        assertEquals("Systems", jdbcTemplate.queryForObject(
            "SELECT name FROM study_group WHERE public_id = ?", String.class, groupId));
    }

    @Test
    void updateGroupReturnsNotFoundForAnotherUsersGroup() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        String otherGroup = createGroupId(otherUser, "Theirs");

        updateGroup(user, otherGroup, new UpdateGroupRequest("Stolen", null))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Group not found: " + otherGroup));

        assertEquals("Theirs", jdbcTemplate.queryForObject(
            "SELECT name FROM study_group WHERE public_id = ?", String.class, otherGroup));
    }

    @Test
    void deleteGroupRemovesItAndReturnsNoContent() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String groupId = createGroupId(user, "Systems");

        mockMvc.perform(delete(GROUP_ENDPOINT, groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        assertEquals(0, countGroups(user.id()));
    }

    @Test
    void deleteGroupReturnsNotFoundForAnotherUsersGroupAndForAnUnknownId() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        String otherGroup = createGroupId(otherUser, "Theirs");

        mockMvc.perform(delete(GROUP_ENDPOINT, otherGroup)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Group not found: " + otherGroup));

        mockMvc.perform(delete(GROUP_ENDPOINT, "missing001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound());

        assertEquals(1, countGroups(otherUser.id()));
    }

    @Test
    void groupRoutesReturnUnauthorizedWhenTokenIsMissing() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String groupId = createGroupId(user, "Systems");

        mockMvc.perform(get(GROUP_LIST_ENDPOINT)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(GROUP_ENDPOINT, groupId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(GROUP_ENDPOINT, groupId)).andExpect(status().isUnauthorized());

        assertEquals(1, countGroups(user.id()));
    }

    private ResultActions createGroup(TestUser user, CreateGroupRequest req) throws Exception {
        return mockMvc.perform(post(GROUPS_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req))
            .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())));
    }

    private ResultActions updateGroup(TestUser user, String groupId, UpdateGroupRequest req) throws Exception {
        return mockMvc.perform(patch(GROUP_ENDPOINT, groupId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req))
            .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())));
    }

    private String createGroupId(TestUser user, String name) throws Exception {
        return groupIdOf(
            createGroup(user, new CreateGroupRequest(name, null))
                .andExpect(status().isCreated())
                .andReturn()
        );
    }

    private void createGroupRows(Long userId, String namePrefix, int count) {
        for (int i = 1; i <= count; i++) {
            createGroupRow(
                userId,
                String.format("grppag%04d", i),
                namePrefix + " " + i,
                String.format("2026-01-01 09:%02d:00", i)
            );
        }
    }

    private String createGroupRow(Long userId, String publicId, String name, String createdAt) {
        jdbcTemplate.update(
            """
            INSERT INTO study_group (user_id, public_id, name, created_at, updated_at)
            VALUES (?, ?, ?, ?::timestamp, ?::timestamp)
            """,
            userId,
            publicId,
            name,
            createdAt,
            createdAt
        );

        return publicId;
    }

    private String groupIdOf(MvcResult result) throws Exception {
        return objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("id")
            .asString();
    }

    private void addNote(TestUser user, String groupId, String noteId) throws Exception {
        mockMvc.perform(put(GROUP_NOTE_ENDPOINT, groupId, noteId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());
    }

    private void addDeck(TestUser user, String groupId, String deckId) throws Exception {
        mockMvc.perform(put(GROUP_DECK_ENDPOINT, groupId, deckId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());
    }

    private void addQuiz(TestUser user, String groupId, String quizId) throws Exception {
        mockMvc.perform(put(GROUP_QUIZ_ENDPOINT, groupId, quizId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());
    }

    private int countGroups(Long userId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM study_group WHERE user_id = ?",
            Integer.class,
            userId
        );
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

    private String createNote(Long userId, String title) {
        String publicId = nanoId();

        jdbcTemplate.update(
            """
            INSERT INTO note (user_id, public_id, title, overview)
            VALUES (?, ?, ?, 'An overview.')
            """,
            userId,
            publicId,
            title
        );

        return publicId;
    }

    private String createDeck(Long userId, String title) {
        String publicId = nanoId();

        jdbcTemplate.update(
            """
            INSERT INTO flashcard_deck (user_id, title, source_type, public_id)
            VALUES (?, ?, 'NOTE', ?)
            """,
            userId,
            title,
            publicId
        );

        return publicId;
    }

    private String createQuiz(Long userId, String title) {
        String publicId = nanoId();

        jdbcTemplate.update(
            """
            INSERT INTO quiz (user_id, public_id, title, description, source_type)
            VALUES (?, ?, ?, 'A saved quiz.', 'NOTE')
            """,
            userId,
            publicId,
            title
        );

        return publicId;
    }

    private String nanoId() {
        return NanoIdUtils.randomNanoId(
            NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
            NanoIdUtils.DEFAULT_ALPHABET,
            10
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

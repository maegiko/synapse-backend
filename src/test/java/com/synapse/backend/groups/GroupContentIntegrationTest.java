package com.synapse.backend.groups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class GroupContentIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String GROUPS_ENDPOINT = "/api/groups";
    private static final String GROUP_ENDPOINT = "/api/groups/{groupId}";
    private static final String GROUP_NOTE_ENDPOINT = "/api/groups/{groupId}/notes/{noteId}";
    private static final String GROUP_DECK_ENDPOINT = "/api/groups/{groupId}/decks/{deckId}";
    private static final String GROUP_QUIZ_ENDPOINT = "/api/groups/{groupId}/quizzes/{quizId}";
    private static final String NOTE_ENDPOINT = "/api/notes/{noteId}";
    private static final String DECK_ENDPOINT = "/api/flashcards/{deckId}";
    private static final String QUIZ_ENDPOINT = "/api/quiz/{quizId}";
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
    void savedResourcesStartUngrouped() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String noteId = createNote(user.id(), "Cells");
        String deckId = createDeck(user.id(), "Cell deck");
        String quizId = createQuiz(user.id(), "Cell quiz");

        assertNull(groupIdColumn("note", noteId));
        assertNull(groupIdColumn("flashcard_deck", deckId));
        assertNull(groupIdColumn("quiz", quizId));

        mockMvc.perform(get(NOTE_ENDPOINT, noteId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groupId").isEmpty());

        mockMvc.perform(get(DECK_ENDPOINT, deckId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groupId").isEmpty());

        mockMvc.perform(get(QUIZ_ENDPOINT, quizId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groupId").isEmpty());
    }

    @Test
    void addedResourcesReportTheirGroupInTheirOwnResponses() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String groupId = createGroupId(user, "Systems");
        String noteId = createNote(user.id(), "Cells");
        String deckId = createDeck(user.id(), "Cell deck");
        String quizId = createQuiz(user.id(), "Cell quiz");

        addNote(user, groupId, noteId).andExpect(status().isNoContent());
        addDeck(user, groupId, deckId).andExpect(status().isNoContent());
        addQuiz(user, groupId, quizId).andExpect(status().isNoContent());

        mockMvc.perform(get(NOTE_ENDPOINT, noteId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(jsonPath("$.groupId").value(groupId));

        mockMvc.perform(get("/api/notes/list")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(jsonPath("$.notes[0].groupId").value(groupId));

        mockMvc.perform(get(DECK_ENDPOINT, deckId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(jsonPath("$.groupId").value(groupId));

        mockMvc.perform(get("/api/flashcards/list")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(jsonPath("$.flashcardDecks[0].groupId").value(groupId));

        mockMvc.perform(get(QUIZ_ENDPOINT, quizId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(jsonPath("$.groupId").value(groupId));

        mockMvc.perform(get("/api/quiz/list")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(jsonPath("$.quizzes[0].groupId").value(groupId));
    }

    @Test
    void addingAGroupedResourceToAnotherGroupMovesIt() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String first = createGroupId(user, "First");
        String second = createGroupId(user, "Second");
        String noteId = createNote(user.id(), "Cells");

        addNote(user, first, noteId).andExpect(status().isNoContent());
        addNote(user, second, noteId).andExpect(status().isNoContent());

        assertEquals(internalGroupId(second), groupIdColumn("note", noteId));

        mockMvc.perform(get(GROUP_ENDPOINT, first)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(jsonPath("$.notes").isEmpty());

        mockMvc.perform(get(GROUP_ENDPOINT, second)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(jsonPath("$.notes.length()").value(1))
            .andExpect(jsonPath("$.notes[0].id").value(noteId));
    }

    @Test
    void addingAResourceTwiceToTheSameGroupIsSafe() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String groupId = createGroupId(user, "Systems");
        String deckId = createDeck(user.id(), "Cell deck");

        addDeck(user, groupId, deckId).andExpect(status().isNoContent());
        addDeck(user, groupId, deckId).andExpect(status().isNoContent());

        mockMvc.perform(get(GROUP_ENDPOINT, groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(jsonPath("$.decks.length()").value(1));
    }

    @Test
    void removingAResourceOnlyUngroupsIt() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String groupId = createGroupId(user, "Systems");
        String noteId = createNote(user.id(), "Cells");
        String deckId = createDeck(user.id(), "Cell deck");
        String quizId = createQuiz(user.id(), "Cell quiz");

        addNote(user, groupId, noteId).andExpect(status().isNoContent());
        addDeck(user, groupId, deckId).andExpect(status().isNoContent());
        addQuiz(user, groupId, quizId).andExpect(status().isNoContent());

        removeNote(user, groupId, noteId).andExpect(status().isNoContent());
        removeDeck(user, groupId, deckId).andExpect(status().isNoContent());
        removeQuiz(user, groupId, quizId).andExpect(status().isNoContent());

        assertNull(groupIdColumn("note", noteId));
        assertNull(groupIdColumn("flashcard_deck", deckId));
        assertNull(groupIdColumn("quiz", quizId));

        assertEquals(1, countRows("note", noteId));
        assertEquals(1, countRows("flashcard_deck", deckId));
        assertEquals(1, countRows("quiz", quizId));

        mockMvc.perform(get(GROUP_ENDPOINT, groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes").isEmpty())
            .andExpect(jsonPath("$.decks").isEmpty())
            .andExpect(jsonPath("$.quizzes").isEmpty());
    }

    @Test
    void removingAResourceFromAGroupItIsNotInReturnsNotFound() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String holder = createGroupId(user, "Holder");
        String other = createGroupId(user, "Other");
        String noteId = createNote(user.id(), "Cells");

        addNote(user, holder, noteId).andExpect(status().isNoContent());

        removeNote(user, other, noteId)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Note could not be found: " + noteId));

        assertEquals(internalGroupId(holder), groupIdColumn("note", noteId));
    }

    @Test
    void deletingAGroupKeepsItsContentAndUngroupsIt() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String groupId = createGroupId(user, "Systems");
        String noteId = createNote(user.id(), "Cells");
        String deckId = createDeck(user.id(), "Cell deck");
        String quizId = createQuiz(user.id(), "Cell quiz");

        addNote(user, groupId, noteId).andExpect(status().isNoContent());
        addDeck(user, groupId, deckId).andExpect(status().isNoContent());
        addQuiz(user, groupId, quizId).andExpect(status().isNoContent());

        mockMvc.perform(delete(GROUP_ENDPOINT, groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        assertEquals(1, countRows("note", noteId));
        assertEquals(1, countRows("flashcard_deck", deckId));
        assertEquals(1, countRows("quiz", quizId));

        assertNull(groupIdColumn("note", noteId));
        assertNull(groupIdColumn("flashcard_deck", deckId));
        assertNull(groupIdColumn("quiz", quizId));
    }

    @Test
    void deletingContentLeavesItsGroupInPlace() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String groupId = createGroupId(user, "Systems");
        String noteId = createNote(user.id(), "Cells");
        String deckId = createDeck(user.id(), "Cell deck");

        addNote(user, groupId, noteId).andExpect(status().isNoContent());
        addDeck(user, groupId, deckId).andExpect(status().isNoContent());

        mockMvc.perform(delete(NOTE_ENDPOINT, noteId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        mockMvc.perform(delete(DECK_ENDPOINT, deckId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(GROUP_ENDPOINT, groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Systems"))
            .andExpect(jsonPath("$.notes").isEmpty())
            .andExpect(jsonPath("$.decks").isEmpty());
    }

    @Test
    void addingAnotherUsersResourceReturnsNotFound() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        String groupId = createGroupId(user, "Systems");

        String otherNote = createNote(otherUser.id(), "Their note");
        String otherDeck = createDeck(otherUser.id(), "Their deck");
        String otherQuiz = createQuiz(otherUser.id(), "Their quiz");

        addNote(user, groupId, otherNote)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Note could not be found: " + otherNote));

        addDeck(user, groupId, otherDeck)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Deck not found: " + otherDeck));

        addQuiz(user, groupId, otherQuiz)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: " + otherQuiz));

        assertNull(groupIdColumn("note", otherNote));
        assertNull(groupIdColumn("flashcard_deck", otherDeck));
        assertNull(groupIdColumn("quiz", otherQuiz));
    }

    @Test
    void addingToAnotherUsersGroupReturnsNotFoundAndLeavesTheResourceUngrouped() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        String otherGroup = createGroupId(otherUser, "Theirs");
        String noteId = createNote(user.id(), "Cells");

        addNote(user, otherGroup, noteId)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Group not found: " + otherGroup));

        assertNull(groupIdColumn("note", noteId));
    }

    @Test
    void removingAnotherUsersResourceReturnsNotFound() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        String otherGroup = createGroupId(otherUser, "Theirs");
        String otherNote = createNote(otherUser.id(), "Their note");

        addNote(otherUser, otherGroup, otherNote).andExpect(status().isNoContent());

        removeNote(user, otherGroup, otherNote)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Group not found: " + otherGroup));

        assertEquals(internalGroupId(otherGroup), groupIdColumn("note", otherNote));
    }

    @Test
    void addingAnUnknownResourceOrGroupReturnsNotFound() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String groupId = createGroupId(user, "Systems");
        String noteId = createNote(user.id(), "Cells");

        addNote(user, groupId, "missing001").andExpect(status().isNotFound());
        addDeck(user, groupId, "missing001").andExpect(status().isNotFound());
        addQuiz(user, groupId, "missing001").andExpect(status().isNotFound());

        addNote(user, "missing002", noteId)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Group not found: missing002"));

        assertNull(groupIdColumn("note", noteId));
    }

    @Test
    void membershipRoutesReturnUnauthorizedWhenTokenIsMissing() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String groupId = createGroupId(user, "Systems");
        String noteId = createNote(user.id(), "Cells");

        mockMvc.perform(put(GROUP_NOTE_ENDPOINT, groupId, noteId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(GROUP_NOTE_ENDPOINT, groupId, noteId)).andExpect(status().isUnauthorized());

        assertNull(groupIdColumn("note", noteId));
    }

    private ResultActions addNote(TestUser user, String groupId, String noteId) throws Exception {
        return mockMvc.perform(put(GROUP_NOTE_ENDPOINT, groupId, noteId)
            .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())));
    }

    private ResultActions removeNote(TestUser user, String groupId, String noteId) throws Exception {
        return mockMvc.perform(delete(GROUP_NOTE_ENDPOINT, groupId, noteId)
            .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())));
    }

    private ResultActions addDeck(TestUser user, String groupId, String deckId) throws Exception {
        return mockMvc.perform(put(GROUP_DECK_ENDPOINT, groupId, deckId)
            .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())));
    }

    private ResultActions removeDeck(TestUser user, String groupId, String deckId) throws Exception {
        return mockMvc.perform(delete(GROUP_DECK_ENDPOINT, groupId, deckId)
            .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())));
    }

    private ResultActions addQuiz(TestUser user, String groupId, String quizId) throws Exception {
        return mockMvc.perform(put(GROUP_QUIZ_ENDPOINT, groupId, quizId)
            .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())));
    }

    private ResultActions removeQuiz(TestUser user, String groupId, String quizId) throws Exception {
        return mockMvc.perform(delete(GROUP_QUIZ_ENDPOINT, groupId, quizId)
            .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())));
    }

    private String createGroupId(TestUser user, String name) throws Exception {
        MvcResult result = mockMvc.perform(post(GROUPS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateGroupRequest(name, null)))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("id")
            .asString();
    }

    private Long groupIdColumn(String table, String publicId) {
        return jdbcTemplate.queryForObject(
            "SELECT group_id FROM " + table + " WHERE public_id = ?",
            Long.class,
            publicId
        );
    }

    private Long internalGroupId(String groupPublicId) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM study_group WHERE public_id = ?",
            Long.class,
            groupPublicId
        );
    }

    private int countRows(String table, String publicId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE public_id = ?",
            Integer.class,
            publicId
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

package com.synapse.backend.flashcards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
class FlashcardDeleteIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String DECK_ENDPOINT = "/api/flashcards/{deckId}";
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
    void doesNotGenerateFlashcards() {
        verifyNoInteractions(llmClient);
    }

    @Test
    void deleteFlashcardDeckDeletesCurrentUsersDeckAndCards() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        UUID deckPublicId = UUID.fromString("00000000-0000-0000-0000-000000001201");
        Long deckId = createDeck(user.id(), deckPublicId, "Systems deck", "2026-01-02 09:00:00");
        createFlashcard(
            deckId,
            UUID.fromString("00000000-0000-0000-0000-000000001301"),
            "What is feedback?",
            "A closed chain of cause and effect.",
            0
        );
        createFlashcard(
            deckId,
            UUID.fromString("00000000-0000-0000-0000-000000001302"),
            "What is a stock?",
            "A quantity measured at one point.",
            1
        );

        mockMvc.perform(delete(DECK_ENDPOINT, deckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(DECK_ENDPOINT, deckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Flashcard deck not found: " + deckPublicId));

        assertEquals(0L, countDeck(deckId));
        assertEquals(0L, countFlashcards(deckId));
    }

    @Test
    void deleteFlashcardDeckDoesNotDeleteDeckOwnedByOtherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        UUID otherUsersDeckPublicId = UUID.fromString("00000000-0000-0000-0000-000000001401");
        Long otherUsersDeckId = createDeck(
            otherUser.id(),
            otherUsersDeckPublicId,
            "Private deck",
            "2026-01-03 09:00:00"
        );
        createFlashcard(
            otherUsersDeckId,
            UUID.fromString("00000000-0000-0000-0000-000000001501"),
            "Hidden question",
            "Hidden answer",
            0
        );

        mockMvc.perform(delete(DECK_ENDPOINT, otherUsersDeckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Flashcard deck not found: " + otherUsersDeckPublicId));

        mockMvc.perform(get(DECK_ENDPOINT, otherUsersDeckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherUser.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deckId").value(otherUsersDeckPublicId.toString()));

        assertEquals(1L, countDeck(otherUsersDeckId));
        assertEquals(1L, countFlashcards(otherUsersDeckId));
    }

    @Test
    void deleteFlashcardDeckReturnsNotFoundWhenDeckDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        UUID missingDeckPublicId = UUID.fromString("00000000-0000-0000-0000-000000001601");

        mockMvc.perform(delete(DECK_ENDPOINT, missingDeckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Flashcard deck not found: " + missingDeckPublicId));
    }

    @Test
    void deleteFlashcardDeckReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(delete(DECK_ENDPOINT, UUID.fromString("00000000-0000-0000-0000-000000001701")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteFlashcardDeckReturnsUnauthorizedWhenTokenIsInvalid() throws Exception {
        mockMvc.perform(delete(DECK_ENDPOINT, UUID.fromString("00000000-0000-0000-0000-000000001801"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteFlashcardDeckReturnsBadRequestWhenDeckIdIsNotUuid() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(delete(DECK_ENDPOINT, "not-a-uuid")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest());
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

    private Long createDeck(Long userId, UUID publicId, String title, String createdAt) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO flashcard_deck (user_id, title, source_type, public_id, created_at, updated_at)
            VALUES (?, ?, 'NOTE', ?::uuid, ?::timestamp, ?::timestamp)
            RETURNING id
            """,
            Long.class,
            userId,
            title,
            publicId,
            createdAt,
            createdAt
        );
    }

    private void createFlashcard(Long deckId, UUID publicId, String question, String answer, int position) {
        jdbcTemplate.update(
            """
            INSERT INTO flashcard (deck_id, question, answer, position, public_id)
            VALUES (?, ?, ?, ?, ?::uuid)
            """,
            deckId,
            question,
            answer,
            position,
            publicId
        );
    }

    private Long countDeck(Long deckId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flashcard_deck WHERE id = ?",
            Long.class,
            deckId
        );
    }

    private Long countFlashcards(Long deckId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flashcard WHERE deck_id = ?",
            Long.class,
            deckId
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

package com.synapse.backend.flashcards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.support.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
class FlashcardDeleteIntegrationTest extends PostgresIntegrationTest {

    private static final String DECK_ENDPOINT = "/api/flashcards/{deckId}";
    private static final String VALID_PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

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
        String deckPublicId = "deckdrp001";
        Long deckId = createDeck(user.id(), deckPublicId, "Systems deck", "2026-01-02 09:00:00");
        createFlashcard(
            deckId,
            "carddrp001",
            "What is feedback?",
            "A closed chain of cause and effect.",
            0
        );
        createFlashcard(
            deckId,
            "carddrp002",
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
        String otherUsersDeckPublicId = "deckdrp002";
        Long otherUsersDeckId = createDeck(
            otherUser.id(),
            otherUsersDeckPublicId,
            "Private deck",
            "2026-01-03 09:00:00"
        );
        createFlashcard(
            otherUsersDeckId,
            "carddrp003",
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
        String missingDeckPublicId = "deckdrp003";

        mockMvc.perform(delete(DECK_ENDPOINT, missingDeckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Flashcard deck not found: " + missingDeckPublicId));
    }

    @Test
    void deleteFlashcardDeckReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(delete(DECK_ENDPOINT, "deckdrp004"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteFlashcardDeckReturnsUnauthorizedWhenTokenIsInvalid() throws Exception {
        mockMvc.perform(delete(DECK_ENDPOINT, "deckdrp005")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }

    private TestUser register(String fullName, String email) throws Exception {
        String accessToken = registerAndAuthenticate(fullName, email, VALID_PASSWORD);
        Long userId = Long.valueOf(jwtDecoder.decode(accessToken).getSubject());

        return new TestUser(userId, accessToken);
    }

    private Long createDeck(Long userId, String publicId, String title, String createdAt) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO flashcard_deck (user_id, title, source_type, public_id, created_at, updated_at)
            VALUES (?, ?, 'NOTE', ?, ?::timestamp, ?::timestamp)
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

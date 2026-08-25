package com.synapse.backend.flashcards;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;

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
class FlashcardCardDeleteIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String DECK_ENDPOINT = "/api/flashcards/{deckId}";
    private static final String CARD_ENDPOINT = "/api/flashcards/{deckId}/cards/{cardId}";
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
    void deleteFlashcardRemovesOnlySelectedCardAndUpdatesDeckTimestamp() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String deckPublicId = "deckdel001";
        String firstCardPublicId = "carddel001";
        String deletedCardPublicId = "carddel002";
        String lastCardPublicId = "carddel003";
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 1, 5, 9, 0);
        Long deckId = createDeck(user.id(), deckPublicId, "Systems deck", originalUpdatedAt);
        createFlashcard(deckId, firstCardPublicId, "First question", "First answer", 0);
        createFlashcard(deckId, deletedCardPublicId, "Deleted question", "Deleted answer", 1);
        createFlashcard(deckId, lastCardPublicId, "Last question", "Last answer", 2);

        mockMvc.perform(delete(CARD_ENDPOINT, deckPublicId, deletedCardPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(DECK_ENDPOINT, deckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flashcards", hasSize(2)))
            .andExpect(jsonPath("$.flashcards[0].id").value(firstCardPublicId.toString()))
            .andExpect(jsonPath("$.flashcards[1].id").value(lastCardPublicId.toString()));

        assertEquals(0L, countFlashcard(deletedCardPublicId));
        assertEquals(2L, countFlashcards(deckId));
        assertTrue(deckUpdatedAt(deckId).isAfter(originalUpdatedAt));
    }

    @Test
    void deleteFlashcardDoesNotDeleteCardFromAnotherDeck() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String requestedDeckPublicId = "deckdel002";
        String cardDeckPublicId = "deckdel003";
        String cardPublicId = "carddel004";
        LocalDateTime cardDeckUpdatedAt = LocalDateTime.of(2026, 1, 6, 9, 0);
        createDeck(user.id(), requestedDeckPublicId, "Requested deck", LocalDateTime.of(2026, 1, 5, 9, 0));
        Long cardDeckId = createDeck(user.id(), cardDeckPublicId, "Card deck", cardDeckUpdatedAt);
        createFlashcard(cardDeckId, cardPublicId, "Question", "Answer", 0);

        mockMvc.perform(delete(CARD_ENDPOINT, requestedDeckPublicId, cardPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound());

        assertEquals(1L, countFlashcard(cardPublicId));
        assertEquals(cardDeckUpdatedAt, deckUpdatedAt(cardDeckId));
    }

    @Test
    void deleteFlashcardDoesNotDeleteCardFromDeckOwnedByAnotherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        String deckPublicId = "deckdel004";
        String cardPublicId = "carddel005";
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 1, 7, 9, 0);
        Long deckId = createDeck(otherUser.id(), deckPublicId, "Private deck", originalUpdatedAt);
        createFlashcard(deckId, cardPublicId, "Private question", "Private answer", 0);

        mockMvc.perform(delete(CARD_ENDPOINT, deckPublicId, cardPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isNotFound());

        assertEquals(1L, countFlashcard(cardPublicId));
        assertEquals(originalUpdatedAt, deckUpdatedAt(deckId));
    }

    @Test
    void deleteFlashcardReturnsNotFoundWhenCardDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String deckPublicId = "deckdel005";
        String missingCardPublicId = "carddel006";
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 1, 8, 9, 0);
        Long deckId = createDeck(user.id(), deckPublicId, "Systems deck", originalUpdatedAt);

        mockMvc.perform(delete(CARD_ENDPOINT, deckPublicId, missingCardPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound());

        assertEquals(originalUpdatedAt, deckUpdatedAt(deckId));
    }

    @Test
    void deleteFlashcardReturnsNotFoundWhenDeckDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(delete(
                CARD_ENDPOINT,
                "deckdel006",
                "carddel007"
            )
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteFlashcardReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(delete(
                CARD_ENDPOINT,
                "deckdel007",
                "carddel008"
            ))
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

    private Long createDeck(Long userId, String publicId, String title, LocalDateTime updatedAt) {
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
            Timestamp.valueOf(updatedAt),
            Timestamp.valueOf(updatedAt)
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

    private Long countFlashcard(String publicId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flashcard WHERE public_id = ?",
            Long.class,
            publicId
        );
    }

    private Long countFlashcards(Long deckId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flashcard WHERE deck_id = ?",
            Long.class,
            deckId
        );
    }

    private LocalDateTime deckUpdatedAt(Long deckId) {
        return jdbcTemplate.queryForObject(
            "SELECT updated_at FROM flashcard_deck WHERE id = ?",
            LocalDateTime.class,
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

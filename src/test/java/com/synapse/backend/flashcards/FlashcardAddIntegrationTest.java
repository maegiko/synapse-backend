package com.synapse.backend.flashcards;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class FlashcardAddIntegrationTest extends PostgresIntegrationTest {

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
    void addFlashcardCreatesCardInCurrentUsersDeckAndReturnsCreatedCard() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String deckPublicId = "deckadd001";
        String existingCardPublicId = "cardadd001";
        LocalDateTime originalDeckUpdatedAt = LocalDateTime.of(2026, 1, 2, 9, 0);
        Long deckId = createDeck(user.id(), deckPublicId, "Systems deck", originalDeckUpdatedAt);
        createFlashcard(
            deckId,
            existingCardPublicId,
            "What is feedback?",
            "A closed chain of cause and effect.",
            0
        );

        MvcResult result = mockMvc.perform(post(DECK_ENDPOINT, deckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "question", "What is a delay?",
                    "answer", "A gap between cause and visible effect."
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.question").value("What is a delay?"))
            .andExpect(jsonPath("$.answer").value("A gap between cause and visible effect."))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String newCardPublicId = response.get("id").asString();

        mockMvc.perform(get(DECK_ENDPOINT, deckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deckId").value(deckPublicId.toString()))
            .andExpect(jsonPath("$.flashcards", hasSize(2)))
            .andExpect(jsonPath("$.flashcards[0].id").value(existingCardPublicId.toString()))
            .andExpect(jsonPath("$.flashcards[1].id").value(newCardPublicId.toString()))
            .andExpect(jsonPath("$.flashcards[1].title").value("What is a delay?"))
            .andExpect(jsonPath("$.flashcards[1].answer").value("A gap between cause and visible effect."));

        assertEquals(1L, countFlashcards(deckId, "What is a delay?", "A gap between cause and visible effect."));
        assertTrue(deckUpdatedAt(deckId).isAfter(originalDeckUpdatedAt));
    }

    @Test
    void addFlashcardDoesNotAddCardToDeckOwnedByOtherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        String otherUsersDeckPublicId = "deckadd002";
        Long otherUsersDeckId = createDeck(
            otherUser.id(),
            otherUsersDeckPublicId,
            "Private deck",
            LocalDateTime.of(2026, 1, 3, 9, 0)
        );

        mockMvc.perform(post(DECK_ENDPOINT, otherUsersDeckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "question", "Hidden question",
                    "answer", "Hidden answer"
                ))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Deck not found: " + otherUsersDeckPublicId));

        assertEquals(0L, countFlashcards(otherUsersDeckId));
    }

    @Test
    void addFlashcardReturnsBadRequestWhenQuestionIsBlank() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String deckPublicId = "deckadd003";
        createDeck(user.id(), deckPublicId, "Systems deck", LocalDateTime.of(2026, 1, 4, 9, 0));

        mockMvc.perform(post(DECK_ENDPOINT, deckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "question", " ",
                    "answer", "A valid answer."
                ))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void addFlashcardReturnsNotFoundWhenDeckDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String missingDeckPublicId = "deckadd004";

        mockMvc.perform(post(DECK_ENDPOINT, missingDeckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "question", "What is a stock?",
                    "answer", "A quantity measured at one point."
                ))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Deck not found: " + missingDeckPublicId));
    }

    @Test
    void addFlashcardReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(post(DECK_ENDPOINT, "deckadd005")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "question", "What is a stock?",
                    "answer", "A quantity measured at one point."
                ))))
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

    private Long countFlashcards(Long deckId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flashcard WHERE deck_id = ?",
            Long.class,
            deckId
        );
    }

    private Long countFlashcards(Long deckId, String question, String answer) {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM flashcard
            WHERE deck_id = ? AND question = ? AND answer = ?
            """,
            Long.class,
            deckId,
            question,
            answer
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

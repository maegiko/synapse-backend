package com.synapse.backend.flashcards;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
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

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class FlashcardCardUpdateIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String CARD_ENDPOINT = "/api/flashcards/{deckId}/cards/{cardId}";
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
    void updateFlashcardUpdatesBothFieldsAdvancesDeckTimestampAndLeavesOtherCardsUnchanged() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        LocalDateTime originalDeckUpdatedAt = LocalDateTime.of(2026, 1, 2, 9, 0);
        Long deckId = createDeck(user.id(), "deckcup001", "Systems deck", originalDeckUpdatedAt);
        createFlashcard(deckId, "cardcup001", "Old question", "Old answer", 0);
        createFlashcard(deckId, "cardcup002", "Other question", "Other answer", 1);

        mockMvc.perform(patch(CARD_ENDPOINT, "deckcup001", "cardcup001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "question", "What is a delay?",
                    "answer", "A gap between cause and visible effect."
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("cardcup001"))
            .andExpect(jsonPath("$.question").value("What is a delay?"))
            .andExpect(jsonPath("$.answer").value("A gap between cause and visible effect."))
            .andExpect(jsonPath("$.createdAt").isNotEmpty());

        assertEquals("What is a delay?", cardColumn(deckId, "cardcup001", "question"));
        assertEquals("A gap between cause and visible effect.", cardColumn(deckId, "cardcup001", "answer"));
        assertEquals("Other question", cardColumn(deckId, "cardcup002", "question"));
        assertTrue(deckUpdatedAt(deckId).isAfter(originalDeckUpdatedAt));
    }

    @Test
    void updateFlashcardUpdatesOnlyQuestion() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeck(user.id(), "deckcup002", "Systems deck", LocalDateTime.of(2026, 1, 3, 9, 0));
        createFlashcard(deckId, "cardcup010", "Old question", "The answer", 0);

        Map<String, Object> body = new HashMap<>();
        body.put("question", "New question");

        mockMvc.perform(patch(CARD_ENDPOINT, "deckcup002", "cardcup010")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.question").value("New question"))
            .andExpect(jsonPath("$.answer").value("The answer"));

        assertEquals("New question", cardColumn(deckId, "cardcup010", "question"));
        assertEquals("The answer", cardColumn(deckId, "cardcup010", "answer"));
    }

    @Test
    void updateFlashcardUpdatesOnlyAnswerAndTrimsIt() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeck(user.id(), "deckcup003", "Systems deck", LocalDateTime.of(2026, 1, 4, 9, 0));
        createFlashcard(deckId, "cardcup020", "The question", "Old answer", 0);

        Map<String, Object> body = new HashMap<>();
        body.put("answer", "  New answer  ");

        mockMvc.perform(patch(CARD_ENDPOINT, "deckcup003", "cardcup020")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.question").value("The question"))
            .andExpect(jsonPath("$.answer").value("New answer"));

        assertEquals("New answer", cardColumn(deckId, "cardcup020", "answer"));
    }

    @Test
    void updateFlashcardRejectsAnEmptyRequest() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeck(user.id(), "deckcup004", "Systems deck", LocalDateTime.of(2026, 1, 5, 9, 0));
        createFlashcard(deckId, "cardcup030", "Old question", "Old answer", 0);

        mockMvc.perform(patch(CARD_ENDPOINT, "deckcup004", "cardcup030")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("At least one of question or answer must be supplied."));

        assertEquals("Old question", cardColumn(deckId, "cardcup030", "question"));
    }

    @Test
    void updateFlashcardRejectsBlankFields() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeck(user.id(), "deckcup005", "Systems deck", LocalDateTime.of(2026, 1, 6, 9, 0));
        createFlashcard(deckId, "cardcup040", "Old question", "Old answer", 0);

        Map<String, Object> body = new HashMap<>();
        body.put("question", "   ");

        mockMvc.perform(patch(CARD_ENDPOINT, "deckcup005", "cardcup040")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("question: must not be blank"));

        assertEquals("Old question", cardColumn(deckId, "cardcup040", "question"));
    }

    @Test
    void updateFlashcardReturnsNotFoundWhenDeckDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(patch(CARD_ENDPOINT, "deckcup404", "cardcup404")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("question", "New question"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Deck not found: deckcup404"));
    }

    @Test
    void updateFlashcardReturnsNotFoundWhenCardDoesNotExistInDeck() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeck(user.id(), "deckcup006", "Systems deck", LocalDateTime.of(2026, 1, 7, 9, 0));

        mockMvc.perform(patch(CARD_ENDPOINT, "deckcup006", "cardcup404")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("question", "New question"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Flashcard not found: cardcup404"));
    }

    @Test
    void updateFlashcardDoesNotUpdateCardInDeckOwnedByAnotherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        LocalDateTime originalDeckUpdatedAt = LocalDateTime.of(2026, 1, 8, 9, 0);
        Long otherDeckId = createDeck(otherUser.id(), "deckcup007", "Private deck", originalDeckUpdatedAt);
        createFlashcard(otherDeckId, "cardcup050", "Private question", "Private answer", 0);

        mockMvc.perform(patch(CARD_ENDPOINT, "deckcup007", "cardcup050")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("question", "Stolen question"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Deck not found: deckcup007"));

        assertEquals("Private question", cardColumn(otherDeckId, "cardcup050", "question"));
        assertEquals(originalDeckUpdatedAt, deckUpdatedAt(otherDeckId));
    }

    @Test
    void updateFlashcardIsVisibleWhenGettingTheDeck() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeck(user.id(), "deckcup008", "Systems deck", LocalDateTime.of(2026, 1, 9, 9, 0));
        createFlashcard(deckId, "cardcup060", "Old question", "Old answer", 0);

        mockMvc.perform(patch(CARD_ENDPOINT, "deckcup008", "cardcup060")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("question", "New question", "answer", "New answer"))))
            .andExpect(status().isOk());

        mockMvc.perform(get(DECK_ENDPOINT, "deckcup008")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flashcards", hasSize(1)))
            .andExpect(jsonPath("$.flashcards[0].id").value("cardcup060"))
            .andExpect(jsonPath("$.flashcards[0].title").value("New question"))
            .andExpect(jsonPath("$.flashcards[0].answer").value("New answer"));
    }

    @Test
    void updateFlashcardReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(patch(CARD_ENDPOINT, "deckcup009", "cardcup070")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("question", "New question"))))
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

    private String cardColumn(Long deckId, String cardPublicId, String column) {
        return jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM flashcard WHERE deck_id = ? AND public_id = ?",
            String.class,
            deckId,
            cardPublicId
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

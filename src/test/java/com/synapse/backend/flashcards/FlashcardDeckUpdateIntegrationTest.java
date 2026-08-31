package com.synapse.backend.flashcards;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class FlashcardDeckUpdateIntegrationTest extends PostgresIntegrationTest {

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
    void updateDeckChangesTitleKeepsCardsAndAdvancesTimestamp() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String deckPublicId = "deckupd001";
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 1, 2, 9, 0);
        Long deckId = createDeck(user.id(), deckPublicId, "Old deck", originalUpdatedAt);
        createFlashcard(deckId, "cardupd001", "What is feedback?", "A closed chain of cause and effect.", 0);

        mockMvc.perform(patch(DECK_ENDPOINT, deckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "Systems deck"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deckId").value(deckPublicId))
            .andExpect(jsonPath("$.title").value("Systems deck"))
            .andExpect(jsonPath("$.flashcards", hasSize(1)))
            .andExpect(jsonPath("$.flashcards[0].id").value("cardupd001"))
            .andExpect(jsonPath("$.flashcards[0].title").value("What is feedback?"))
            .andExpect(jsonPath("$.flashcards[0].answer").value("A closed chain of cause and effect."));

        assertEquals("Systems deck", deckTitle(deckId));
        assertTrue(deckUpdatedAt(deckId).isAfter(originalUpdatedAt));
    }

    @Test
    void updateDeckTrimsTheTitle() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeck(user.id(), "deckupd002", "Old deck", LocalDateTime.of(2026, 1, 3, 9, 0));

        mockMvc.perform(patch(DECK_ENDPOINT, "deckupd002")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "  Systems deck  "))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Systems deck"));

        assertEquals("Systems deck", deckTitle(deckId));
    }

    @Test
    void updateDeckRejectsBlankTitle() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeck(user.id(), "deckupd003", "Old deck", LocalDateTime.of(2026, 1, 4, 9, 0));

        mockMvc.perform(patch(DECK_ENDPOINT, "deckupd003")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "   "))))
            .andExpect(status().isBadRequest());

        assertEquals("Old deck", deckTitle(deckId));
    }

    @Test
    void updateDeckRejectsAnEmptyRequest() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeck(user.id(), "deckupd004", "Old deck", LocalDateTime.of(2026, 1, 5, 9, 0));

        mockMvc.perform(patch(DECK_ENDPOINT, "deckupd004")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());

        assertEquals("Old deck", deckTitle(deckId));
    }

    @Test
    void updateDeckReturnsNotFoundWhenDeckDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(patch(DECK_ENDPOINT, "deckupd404")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "Systems deck"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Flashcard deck not found: deckupd404"));
    }

    @Test
    void updateDeckDoesNotUpdateDeckOwnedByAnotherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        Long otherDeckId = createDeck(otherUser.id(), "deckupd005", "Private deck", LocalDateTime.of(2026, 1, 6, 9, 0));

        mockMvc.perform(patch(DECK_ENDPOINT, "deckupd005")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "Stolen deck"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Flashcard deck not found: deckupd005"));

        assertEquals("Private deck", deckTitle(otherDeckId));
    }

    @Test
    void updateDeckReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(patch(DECK_ENDPOINT, "deckupd006")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "Systems deck"))))
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

    private String deckTitle(Long deckId) {
        return jdbcTemplate.queryForObject("SELECT title FROM flashcard_deck WHERE id = ?", String.class, deckId);
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

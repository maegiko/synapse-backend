package com.synapse.backend.flashcards;

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
class FlashcardGetIntegrationTest extends PostgresIntegrationTest {

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
    void getSingleFlashcardDeckReturnsCurrentUsersDeckAndCardsOrderedByPosition() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String deckPublicId = "deckget001";
        String firstCardPublicId = "cardget001";
        String secondCardPublicId = "cardget002";
        Long deckId = createDeck(user.id(), deckPublicId, "Systems deck", "2026-01-02 09:00:00");
        createFlashcard(deckId, secondCardPublicId, "What is a stock?", "A quantity measured at one point.", 1);
        createFlashcard(deckId, firstCardPublicId, "What is feedback?", "A closed chain of cause and effect.", 0);

        mockMvc.perform(get(DECK_ENDPOINT, deckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deckId").value(deckPublicId.toString()))
            .andExpect(jsonPath("$.title").value("Systems deck"))
            .andExpect(jsonPath("$.pinned").value(false))
            .andExpect(jsonPath("$.flashcards", hasSize(2)))
            .andExpect(jsonPath("$.flashcards[0].id").value(firstCardPublicId.toString()))
            .andExpect(jsonPath("$.flashcards[0].title").value("What is feedback?"))
            .andExpect(jsonPath("$.flashcards[0].answer").value("A closed chain of cause and effect."))
            .andExpect(jsonPath("$.flashcards[1].id").value(secondCardPublicId.toString()))
            .andExpect(jsonPath("$.flashcards[1].title").value("What is a stock?"))
            .andExpect(jsonPath("$.flashcards[1].answer").value("A quantity measured at one point."));
    }

    @Test
    void getSingleFlashcardDeckDoesNotReturnDeckOwnedByOtherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        String otherUsersDeckPublicId = "deckget002";
        Long otherUsersDeckId = createDeck(
            otherUser.id(),
            otherUsersDeckPublicId,
            "Private deck",
            "2026-01-03 09:00:00"
        );
        createFlashcard(
            otherUsersDeckId,
            "cardget003",
            "Hidden question",
            "Hidden answer",
            0
        );

        mockMvc.perform(get(DECK_ENDPOINT, otherUsersDeckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Flashcard deck not found: " + otherUsersDeckPublicId));
    }

    @Test
    void getSingleFlashcardDeckReturnsNotFoundWhenDeckDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String missingDeckPublicId = "deckget003";

        mockMvc.perform(get(DECK_ENDPOINT, missingDeckPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Flashcard deck not found: " + missingDeckPublicId));
    }

    @Test
    void getSingleFlashcardDeckReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get(DECK_ENDPOINT, "deckget004"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getSingleFlashcardDeckReturnsUnauthorizedWhenTokenIsInvalid() throws Exception {
        mockMvc.perform(get(DECK_ENDPOINT, "deckget005")
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

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record TestUser(
        Long id,
        String accessToken
    ) {}
}

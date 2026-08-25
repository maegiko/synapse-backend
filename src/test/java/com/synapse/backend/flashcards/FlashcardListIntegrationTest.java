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
class FlashcardListIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String LIST_ENDPOINT = "/api/flashcards/list";
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
    void listFlashcardsReturnsCurrentUsersDecksAndCards() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        String firstDeckPublicId = "decklst001";
        String secondDeckPublicId = "decklst002";
        String firstCardPublicId = "cardlst001";
        String secondCardPublicId = "cardlst002";

        Long olderDeckId = createDeck(
            user.id(),
            firstDeckPublicId,
            "Older deck",
            "2026-01-01 09:00:00"
        );
        Long newerDeckId = createDeck(
            user.id(),
            secondDeckPublicId,
            "Newer deck",
            "2026-01-02 09:00:00"
        );
        createFlashcard(
            newerDeckId,
            secondCardPublicId,
            "What is feedback?",
            "A closed chain of cause and effect.",
            1
        );
        createFlashcard(
            olderDeckId,
            firstCardPublicId,
            "What is a stock?",
            "A quantity measured at a point in time.",
            0
        );

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flashcardDecks", hasSize(2)))
            .andExpect(jsonPath("$.flashcardDecks[0].deckId").value(secondDeckPublicId.toString()))
            .andExpect(jsonPath("$.flashcardDecks[0].title").value("Newer deck"))
            .andExpect(jsonPath("$.flashcardDecks[0].flashcards", hasSize(1)))
            .andExpect(jsonPath("$.flashcardDecks[0].flashcards[0].id").value(secondCardPublicId.toString()))
            .andExpect(jsonPath("$.flashcardDecks[0].flashcards[0].title").value("What is feedback?"))
            .andExpect(jsonPath("$.flashcardDecks[0].flashcards[0].answer").value("A closed chain of cause and effect."))
            .andExpect(jsonPath("$.flashcardDecks[1].deckId").value(firstDeckPublicId.toString()))
            .andExpect(jsonPath("$.flashcardDecks[1].title").value("Older deck"))
            .andExpect(jsonPath("$.flashcardDecks[1].flashcards", hasSize(1)))
            .andExpect(jsonPath("$.flashcardDecks[1].flashcards[0].id").value(firstCardPublicId.toString()))
            .andExpect(jsonPath("$.flashcardDecks[1].flashcards[0].title").value("What is a stock?"))
            .andExpect(jsonPath("$.flashcardDecks[1].flashcards[0].answer").value("A quantity measured at a point in time."));
    }

    @Test
    void listFlashcardsReturnsEmptyArrayWhenUserHasNoFlashcards() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flashcardDecks", hasSize(0)));
    }

    @Test
    void listFlashcardsDoesNotReturnFlashcardsOwnedByOtherUsers() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        String visibleDeckPublicId = "decklst003";
        String hiddenDeckPublicId = "decklst004";
        String visibleCardPublicId = "cardlst003";

        Long visibleDeckId = createDeck(
            currentUser.id(),
            visibleDeckPublicId,
            "Visible deck",
            "2026-01-02 09:00:00"
        );
        Long hiddenDeckId = createDeck(
            otherUser.id(),
            hiddenDeckPublicId,
            "Hidden deck",
            "2026-01-03 09:00:00"
        );
        createFlashcard(visibleDeckId, visibleCardPublicId, "Visible question", "Visible answer", 0);
        createFlashcard(
            hiddenDeckId,
            "cardlst004",
            "Hidden question",
            "Hidden answer",
            0
        );

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flashcardDecks", hasSize(1)))
            .andExpect(jsonPath("$.flashcardDecks[0].deckId").value(visibleDeckPublicId.toString()))
            .andExpect(jsonPath("$.flashcardDecks[0].flashcards", hasSize(1)))
            .andExpect(jsonPath("$.flashcardDecks[0].flashcards[0].id").value(visibleCardPublicId.toString()))
            .andExpect(jsonPath("$.flashcardDecks[0].flashcards[0].title").value("Visible question"))
            .andExpect(jsonPath("$.flashcardDecks[0].flashcards[0].answer").value("Visible answer"));
    }

    @Test
    void listFlashcardsReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get(LIST_ENDPOINT))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listFlashcardsReturnsUnauthorizedWhenTokenIsInvalid() throws Exception {
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

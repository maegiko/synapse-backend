package com.synapse.backend.flashcards;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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

import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class FlashcardReviewQueueIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String REVIEW_QUEUE_ENDPOINT = "/api/flashcards/review";
    private static final String VALID_PASSWORD = "password123";
    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
    private static final LocalDateTime REVIEWED_AT = TODAY.atTime(12, 0);
    private static final Instant MIDDAY = REVIEWED_AT.toInstant(ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void resetDatabaseAndClock() {
        jdbcTemplate.execute("DELETE FROM app_user");
        when(clock.instant()).thenReturn(MIDDAY);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void reviewQueueIsEmptyWhenTheUserHasNoDecks() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(REVIEW_QUEUE_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decks").isEmpty());
    }

    @Test
    void newDecksAreDueImmediately() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeck(user.id(), "decknew001", "Systems deck");
        createCards(deckId, "decknew001", 3);

        mockMvc.perform(get(REVIEW_QUEUE_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decks.length()").value(1))
            .andExpect(jsonPath("$.decks[0].deckId").value("decknew001"))
            .andExpect(jsonPath("$.decks[0].title").value("Systems deck"))
            .andExpect(jsonPath("$.decks[0].cardCount").value(3))
            .andExpect(jsonPath("$.decks[0].nextReviewDate").value(TODAY.toString()))
            .andExpect(jsonPath("$.decks[0].intervalDays").value(0))
            .andExpect(jsonPath("$.decks[0].reviewCount").value(0))
            .andExpect(jsonPath("$.decks[0].lastReviewedAt").isEmpty())
            .andExpect(jsonPath("$.decks[0].lastRating").isEmpty());
    }

    @Test
    void decksDueTodayAndEarlierAreQueuedAndLaterDecksAreNot() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDueDeck(user.id(), "deckdue001", "Overdue deck", TODAY.minusDays(3));
        createDueDeck(user.id(), "deckdue002", "Due today", TODAY);
        createDueDeck(user.id(), "deckdue003", "Due tomorrow", TODAY.plusDays(1));

        mockMvc.perform(get(REVIEW_QUEUE_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decks.length()").value(2))
            .andExpect(jsonPath("$.decks[0].deckId").value("deckdue001"))
            .andExpect(jsonPath("$.decks[1].deckId").value("deckdue002"));
    }

    @Test
    void queuedDecksAreOrderedByOldestDueDateWithAStableTieBreaker() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDueDeck(user.id(), "decktie001", "First saved", TODAY);
        createDueDeck(user.id(), "decktie002", "Second saved", TODAY);
        createDueDeck(user.id(), "decktie003", "Oldest due", TODAY.minusDays(5));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(get(REVIEW_QUEUE_ENDPOINT)
                    .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decks.length()").value(3))
                .andExpect(jsonPath("$.decks[0].deckId").value("decktie003"))
                .andExpect(jsonPath("$.decks[1].deckId").value("decktie001"))
                .andExpect(jsonPath("$.decks[2].deckId").value("decktie002"));
        }
    }

    @Test
    void reviewQueueOnlyContainsDecksOwnedByTheAuthenticatedUser() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        createDueDeck(user.id(), "deckmine01", "My deck", TODAY);
        createDueDeck(otherUser.id(), "deckother1", "Their deck", TODAY.minusDays(2));

        mockMvc.perform(get(REVIEW_QUEUE_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decks.length()").value(1))
            .andExpect(jsonPath("$.decks[0].deckId").value("deckmine01"));
    }

    @Test
    void reviewedDecksLeaveTheQueueUntilTheyAreDueAgain() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeck(user.id(), "deckcycle1", "Systems deck");
        createCards(deckId, "deckcycle1", 2);

        mockMvc.perform(post("/api/flashcards/{deckId}/review", "deckcycle1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"EASY\"}")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        mockMvc.perform(get(REVIEW_QUEUE_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decks").isEmpty());

        when(clock.instant()).thenReturn(MIDDAY.plusSeconds(4 * 24 * 60 * 60));

        mockMvc.perform(get(REVIEW_QUEUE_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decks.length()").value(1))
            .andExpect(jsonPath("$.decks[0].deckId").value("deckcycle1"))
            .andExpect(jsonPath("$.decks[0].intervalDays").value(4))
            .andExpect(jsonPath("$.decks[0].reviewCount").value(1))
            .andExpect(jsonPath("$.decks[0].lastReviewedAt").value(TODAY + "T12:00:00"))
            .andExpect(jsonPath("$.decks[0].lastRating").value("EASY"));
    }

    @Test
    void reviewQueueReportsTheMostRecentRatingOfEachDeck() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long hardDeckId = createDeck(user.id(), "deckrate01", "Hard deck");
        createCards(hardDeckId, "deckrate01", 2);
        Long againDeckId = createDeck(user.id(), "deckrate02", "Again deck");
        createCards(againDeckId, "deckrate02", 2);

        review(user, "deckrate01", "GOOD");
        review(user, "deckrate01", "HARD");
        review(user, "deckrate02", "AGAIN");

        when(clock.instant()).thenReturn(MIDDAY.plusSeconds(2 * 24 * 60 * 60));

        mockMvc.perform(get(REVIEW_QUEUE_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decks.length()").value(2))
            .andExpect(jsonPath("$.decks[0].deckId").value("deckrate01"))
            .andExpect(jsonPath("$.decks[0].lastRating").value("HARD"))
            .andExpect(jsonPath("$.decks[1].deckId").value("deckrate02"))
            .andExpect(jsonPath("$.decks[1].lastRating").value("AGAIN"));
    }

    @Test
    void reviewQueueReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get(REVIEW_QUEUE_ENDPOINT))
            .andExpect(status().isUnauthorized());
    }

    private void review(TestUser user, String deckId, String rating) throws Exception {
        mockMvc.perform(post("/api/flashcards/{deckId}/review", deckId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"" + rating + "\"}")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());
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

    private Long createDeck(Long userId, String publicId, String title) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO flashcard_deck (user_id, title, source_type, public_id, next_review_date)
            VALUES (?, ?, 'NOTE', ?, ?)
            RETURNING id
            """,
            Long.class,
            userId,
            title,
            publicId,
            TODAY
        );
    }

    private void createDueDeck(Long userId, String publicId, String title, LocalDate nextReviewDate) {
        Long deckId = createDeck(userId, publicId, title);

        jdbcTemplate.update(
            "UPDATE flashcard_deck SET next_review_date = ? WHERE id = ?",
            nextReviewDate,
            deckId
        );

        createCards(deckId, publicId, 1);
    }

    private void createCards(Long deckId, String deckPublicId, int numberOfCards) {
        for (int position = 0; position < numberOfCards; position++) {
            jdbcTemplate.update(
                """
                INSERT INTO flashcard (deck_id, question, answer, position, public_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                deckId,
                "Question " + position,
                "Answer " + position,
                position,
                deckPublicId.substring(4) + "c" + position
            );
        }
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record TestUser(
        Long id,
        String accessToken
    ) {}
}

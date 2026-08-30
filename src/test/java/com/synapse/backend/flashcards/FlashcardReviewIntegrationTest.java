package com.synapse.backend.flashcards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class FlashcardReviewIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String REVIEW_ENDPOINT = "/api/flashcards/{deckId}/review";
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
    private LLMClient llmClient;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void resetDatabaseAndClock() {
        jdbcTemplate.execute("DELETE FROM app_user");
        when(clock.instant()).thenReturn(MIDDAY);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @AfterEach
    void doesNotGenerateFlashcards() {
        verifyNoInteractions(llmClient);
    }

    @Test
    void reviewingANewDeckWithAgainSchedulesItForTomorrowAndLowersEase() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeckWithCards(user.id(), "deckagain1", 3);

        review(user, "deckagain1", "AGAIN")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deckId").value("deckagain1"))
            .andExpect(jsonPath("$.rating").value("AGAIN"))
            .andExpect(jsonPath("$.intervalDays").value(1))
            .andExpect(jsonPath("$.nextReviewDate").value(TODAY.plusDays(1).toString()))
            .andExpect(jsonPath("$.cardsReviewed").value(3))
            .andExpect(jsonPath("$.totalFlashcardsReviewed").value(3));

        assertSchedule("deckagain1", 1, 1, "2.30", TODAY.plusDays(1));
    }

    @Test
    void reviewingANewDeckWithHardSchedulesOneDayAndLowersEase() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeckWithCards(user.id(), "deckhard01", 2);

        review(user, "deckhard01", "HARD")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.intervalDays").value(1))
            .andExpect(jsonPath("$.nextReviewDate").value(TODAY.plusDays(1).toString()));

        assertSchedule("deckhard01", 1, 1, "2.35", TODAY.plusDays(1));
    }

    @Test
    void reviewingANewDeckWithGoodSchedulesOneDayAndKeepsEase() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeckWithCards(user.id(), "deckgood01", 2);

        review(user, "deckgood01", "GOOD")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.intervalDays").value(1));

        assertSchedule("deckgood01", 1, 1, "2.50", TODAY.plusDays(1));
    }

    @Test
    void reviewingANewDeckWithEasyUsesTheFourDayMinimumAndRaisesEase() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeckWithCards(user.id(), "deckeasy01", 2);

        review(user, "deckeasy01", "EASY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.intervalDays").value(4))
            .andExpect(jsonPath("$.nextReviewDate").value(TODAY.plusDays(4).toString()));

        assertSchedule("deckeasy01", 1, 4, "2.65", TODAY.plusDays(4));
    }

    @Test
    void reviewingAOneDayIntervalWithGoodSchedulesSixDays() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeckWithCards(user.id(), "deckgood02", 2);
        setSchedule(deckId, 1, 1, "2.50");

        review(user, "deckgood02", "GOOD")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.intervalDays").value(6));

        assertSchedule("deckgood02", 2, 6, "2.50", TODAY.plusDays(6));
    }

    @Test
    void reviewingALongerIntervalWithGoodScalesItByTheEaseFactor() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeckWithCards(user.id(), "deckgood03", 2);
        setSchedule(deckId, 2, 6, "2.50");

        review(user, "deckgood03", "GOOD")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.intervalDays").value(15));

        assertSchedule("deckgood03", 3, 15, "2.50", TODAY.plusDays(15));
    }

    @Test
    void reviewingALongerIntervalWithHardScalesItByOnePointTwo() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeckWithCards(user.id(), "deckhard02", 2);
        setSchedule(deckId, 3, 10, "2.50");

        review(user, "deckhard02", "HARD")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.intervalDays").value(12));

        assertSchedule("deckhard02", 4, 12, "2.35", TODAY.plusDays(12));
    }

    @Test
    void reviewingALongerIntervalWithEasyScalesItByEaseAndOnePointThree() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeckWithCards(user.id(), "deckeasy02", 2);
        setSchedule(deckId, 3, 10, "2.50");

        review(user, "deckeasy02", "EASY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.intervalDays").value(33));

        assertSchedule("deckeasy02", 4, 33, "2.65", TODAY.plusDays(33));
    }

    @Test
    void reviewingALongerIntervalWithAgainResetsItToOneDay() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeckWithCards(user.id(), "deckagain2", 2);
        setSchedule(deckId, 4, 20, "2.50");

        review(user, "deckagain2", "AGAIN")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.intervalDays").value(1));

        assertSchedule("deckagain2", 5, 1, "2.30", TODAY.plusDays(1));
    }

    @Test
    void easeFactorNeverFallsBelowTheMinimum() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeckWithCards(user.id(), "deckease01", 2);
        setSchedule(deckId, 5, 5, "1.40");

        review(user, "deckease01", "AGAIN").andExpect(status().isOk());

        assertSchedule("deckease01", 6, 1, "1.30", TODAY.plusDays(1));

        review(user, "deckease01", "HARD").andExpect(status().isOk());

        assertSchedule("deckease01", 7, 1, "1.30", TODAY.plusDays(1));
    }

    @Test
    void reviewingADeckSavesItsReviewHistory() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeckWithCards(user.id(), "deckhist01", 4);
        setSchedule(deckId, 1, 6, "2.50");

        review(user, "deckhist01", "HARD").andExpect(status().isOk());

        assertEquals(
            List.of(
                new TestReview(
                    "HARD",
                    4,
                    4,
                    6,
                    7,
                    new BigDecimal("2.50"),
                    new BigDecimal("2.35"),
                    REVIEWED_AT
                )
            ),
            reviewHistory(deckId)
        );
    }

    @Test
    void reviewingADeckAgainAppendsAnotherHistoryRow() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeckWithCards(user.id(), "deckhist02", 2);

        review(user, "deckhist02", "GOOD").andExpect(status().isOk());
        review(user, "deckhist02", "GOOD").andExpect(status().isOk());

        assertEquals(2, reviewHistory(deckId).size());
    }

    @Test
    void repeatedReviewsIncreaseTheLifetimeCardsReviewedCount() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeckWithCards(user.id(), "decklife01", 3);
        createDeckWithCards(user.id(), "decklife02", 5);

        review(user, "decklife01", "GOOD")
            .andExpect(jsonPath("$.totalFlashcardsReviewed").value(3));
        review(user, "decklife02", "GOOD")
            .andExpect(jsonPath("$.totalFlashcardsReviewed").value(8));
        review(user, "decklife01", "GOOD")
            .andExpect(jsonPath("$.totalFlashcardsReviewed").value(11));

        assertEquals(11L, totalFlashcardsReviewed(user.id()));
    }

    @Test
    void oneUsersReviewsDoNotChangeAnotherUsersLifetimeCount() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        createDeckWithCards(user.id(), "deckuser01", 3);

        review(user, "deckuser01", "GOOD").andExpect(status().isOk());

        assertEquals(3L, totalFlashcardsReviewed(user.id()));
        assertEquals(0L, totalFlashcardsReviewed(otherUser.id()));
    }

    @Test
    void reviewingADeckRecordsStreakActivityForTheCurrentUtcDay() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeckWithCards(user.id(), "deckstrk01", 2);

        review(user, "deckstrk01", "GOOD").andExpect(status().isOk());

        assertEquals(List.of(TODAY), activityDates(user.id()));
    }

    @Test
    void reviewingAnEmptyDeckIsRejectedAndChangesNothing() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeckWithCards(user.id(), "deckempty1", 0);

        review(user, "deckempty1", "GOOD")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Deck has no flashcards to review: deckempty1"));

        assertSchedule("deckempty1", 0, 0, "2.50", TODAY);
        assertEquals(List.of(), reviewHistory(deckId));
        assertEquals(0L, totalFlashcardsReviewed(user.id()));
        assertEquals(List.of(), activityDates(user.id()));
    }

    @Test
    void reviewingADeckOwnedByAnotherUserIsRejectedAndChangesNothing() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        Long deckId = createDeckWithCards(otherUser.id(), "deckowner1", 3);

        review(user, "deckowner1", "GOOD")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Deck not found: deckowner1"));

        assertSchedule("deckowner1", 0, 0, "2.50", TODAY);
        assertEquals(List.of(), reviewHistory(deckId));
        assertEquals(0L, totalFlashcardsReviewed(user.id()));
        assertEquals(0L, totalFlashcardsReviewed(otherUser.id()));
        assertEquals(List.of(), activityDates(user.id()));
    }

    @Test
    void reviewingADeckThatDoesNotExistReturnsNotFound() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        review(user, "missing001", "GOOD")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Deck not found: missing001"));

        assertEquals(0L, totalFlashcardsReviewed(user.id()));
        assertEquals(List.of(), activityDates(user.id()));
    }

    @Test
    void reviewingADeckWithoutARatingIsRejected() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeckWithCards(user.id(), "deckvalid1", 2);

        mockMvc.perform(post(REVIEW_ENDPOINT, "deckvalid1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("rating: must not be null"));

        assertSchedule("deckvalid1", 0, 0, "2.50", TODAY);
        assertEquals(0L, totalFlashcardsReviewed(user.id()));
    }

    @Test
    void reviewingADeckWithAnUnknownRatingIsRejected() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeckWithCards(user.id(), "deckvalid2", 2);

        review(user, "deckvalid2", "PERFECT").andExpect(status().isBadRequest());

        assertSchedule("deckvalid2", 0, 0, "2.50", TODAY);
        assertEquals(0L, totalFlashcardsReviewed(user.id()));
    }

    @Test
    void reviewingADeckReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeckWithCards(user.id(), "deckauth01", 2);

        mockMvc.perform(post(REVIEW_ENDPOINT, "deckauth01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("rating", "GOOD"))))
            .andExpect(status().isUnauthorized());

        assertSchedule("deckauth01", 0, 0, "2.50", TODAY);
        assertEquals(0L, totalFlashcardsReviewed(user.id()));
    }

    private ResultActions review(TestUser user, String deckId, String rating) throws Exception {
        return mockMvc.perform(post(REVIEW_ENDPOINT, deckId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("rating", rating)))
            .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())));
    }

    private void assertSchedule(
        String deckPublicId,
        int reviewCount,
        int intervalDays,
        String easeFactor,
        LocalDate nextReviewDate
    ) {
        TestSchedule schedule = schedule(deckPublicId);

        assertEquals(reviewCount, schedule.reviewCount());
        assertEquals(intervalDays, schedule.intervalDays());
        assertEquals(new BigDecimal(easeFactor), schedule.easeFactor());
        assertEquals(nextReviewDate, schedule.nextReviewDate());
        assertEquals(reviewCount == 0 ? null : REVIEWED_AT, schedule.lastReviewedAt());
    }

    private TestSchedule schedule(String deckPublicId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT review_count, interval_days, ease_factor, next_review_date, last_reviewed_at
            FROM flashcard_deck
            WHERE public_id = ?
            """,
            (rs, rowNum) -> new TestSchedule(
                rs.getInt("review_count"),
                rs.getInt("interval_days"),
                rs.getBigDecimal("ease_factor"),
                rs.getObject("next_review_date", LocalDate.class),
                rs.getObject("last_reviewed_at", LocalDateTime.class)
            ),
            deckPublicId
        );
    }

    private List<TestReview> reviewHistory(Long deckId) {
        return jdbcTemplate.query(
            """
            SELECT rating,
                   cards_reviewed,
                   total_cards,
                   previous_interval_days,
                   new_interval_days,
                   previous_ease_factor,
                   new_ease_factor,
                   reviewed_at
            FROM flashcard_deck_review
            WHERE deck_id = ?
            ORDER BY id ASC
            """,
            (rs, rowNum) -> new TestReview(
                rs.getString("rating"),
                rs.getInt("cards_reviewed"),
                rs.getInt("total_cards"),
                rs.getInt("previous_interval_days"),
                rs.getInt("new_interval_days"),
                rs.getBigDecimal("previous_ease_factor"),
                rs.getBigDecimal("new_ease_factor"),
                rs.getObject("reviewed_at", LocalDateTime.class)
            ),
            deckId
        );
    }

    private long totalFlashcardsReviewed(Long userId) {
        return jdbcTemplate.queryForObject(
            "SELECT total_flashcards_reviewed FROM app_user WHERE id = ?",
            Long.class,
            userId
        );
    }

    private List<LocalDate> activityDates(Long userId) {
        return jdbcTemplate.query(
            """
            SELECT activity_date
            FROM streak_activity
            WHERE user_id = ?
            ORDER BY activity_date ASC
            """,
            (rs, rowNum) -> rs.getObject("activity_date", LocalDate.class),
            userId
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

    private Long createDeckWithCards(Long userId, String publicId, int numberOfCards) {
        Long deckId = jdbcTemplate.queryForObject(
            """
            INSERT INTO flashcard_deck (user_id, title, source_type, public_id)
            VALUES (?, 'Systems deck', 'NOTE', ?)
            RETURNING id
            """,
            Long.class,
            userId,
            publicId
        );

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
                publicId.substring(4) + "c" + position
            );
        }

        return deckId;
    }

    private void setSchedule(Long deckId, int reviewCount, int intervalDays, String easeFactor) {
        jdbcTemplate.update(
            """
            UPDATE flashcard_deck
            SET review_count = ?, interval_days = ?, ease_factor = CAST(? AS NUMERIC)
            WHERE id = ?
            """,
            reviewCount,
            intervalDays,
            easeFactor,
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

    private record TestSchedule(
        int reviewCount,
        int intervalDays,
        BigDecimal easeFactor,
        LocalDate nextReviewDate,
        LocalDateTime lastReviewedAt
    ) {}

    private record TestReview(
        String rating,
        int cardsReviewed,
        int totalCards,
        int previousIntervalDays,
        int newIntervalDays,
        BigDecimal previousEaseFactor,
        BigDecimal newEaseFactor,
        LocalDateTime reviewedAt
    ) {}
}

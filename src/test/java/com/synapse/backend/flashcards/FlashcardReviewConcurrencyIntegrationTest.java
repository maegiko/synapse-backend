package com.synapse.backend.flashcards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.flashcards.dto.review.ReviewDeckRequest;
import com.synapse.backend.flashcards.enums.ReviewRating;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class FlashcardReviewConcurrencyIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String REVIEW_ENDPOINT = "/api/flashcards/{deckId}/review";
    private static final String VALID_PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void concurrentReviewsOfTheSameDeckAreAppliedOneAfterTheOther() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeckWithCards(user.id(), "deckrace01", 3);

        List<TestReviewResult> results = reviewConcurrently(user, "deckrace01", 2);

        assertEquals(List.of(200, 200), results.stream().map(TestReviewResult::status).sorted().toList());
        assertEquals(List.of(1, 6), results.stream().map(TestReviewResult::intervalDays).sorted().toList());
        assertEquals(
            List.of(3L, 6L),
            results.stream().map(TestReviewResult::totalFlashcardsReviewed).sorted().toList()
        );
    }

    @Test
    void concurrentReviewsLeaveTheDeckOnTheSecondSchedule() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeckWithCards(user.id(), "deckrace02", 3);

        reviewConcurrently(user, "deckrace02", 2);

        assertEquals(2, deckColumn("deckrace02", "review_count", Integer.class));
        assertEquals(6, deckColumn("deckrace02", "interval_days", Integer.class));
        assertEquals(new BigDecimal("2.50"), deckColumn("deckrace02", "ease_factor", BigDecimal.class));
        assertEquals(
            LocalDate.now(ZoneOffset.UTC).plusDays(6),
            deckColumn("deckrace02", "next_review_date", LocalDate.class)
        );
    }

    @Test
    void concurrentReviewsChainTheirHistoryRows() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeckWithCards(user.id(), "deckrace03", 3);

        reviewConcurrently(user, "deckrace03", 2);

        assertEquals(List.of(new TestReviewHistory(0, 1), new TestReviewHistory(1, 6)), reviewHistory(deckId));
    }

    @Test
    void concurrentReviewsCountTowardsTheLifetimeTotalOnceEach() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeckWithCards(user.id(), "deckrace04", 3);

        reviewConcurrently(user, "deckrace04", 2);

        assertEquals(6L, totalFlashcardsReviewed(user.id()));
    }

    @Test
    void concurrentReviewsRecordOneStreakDay() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeckWithCards(user.id(), "deckrace05", 3);

        reviewConcurrently(user, "deckrace05", 2);

        assertEquals(List.of(LocalDate.now(ZoneOffset.UTC)), activityDates(user.id()));
    }

    private List<TestReviewResult> reviewConcurrently(TestUser user, String deckId, int attempts) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CyclicBarrier barrier = new CyclicBarrier(attempts);
        List<Future<TestReviewResult>> futures = new ArrayList<>();

        for (int attempt = 0; attempt < attempts; attempt++) {
            futures.add(executor.submit(() -> {
                barrier.await();

                MvcResult result = mockMvc.perform(post(REVIEW_ENDPOINT, deckId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewDeckRequest(ReviewRating.GOOD)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
                    .andReturn();

                return toReviewResult(result);
            }));
        }

        List<TestReviewResult> results = new ArrayList<>();

        for (Future<TestReviewResult> future : futures) {
            results.add(future.get());
        }

        executor.shutdown();

        return results;
    }

    private TestReviewResult toReviewResult(MvcResult result) throws Exception {
        int status = result.getResponse().getStatus();

        if (status != 200)
            return new TestReviewResult(status, 0, 0L);

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        return new TestReviewResult(
            status,
            body.get("intervalDays").asInt(),
            body.get("totalFlashcardsReviewed").asLong()
        );
    }

    private <T> T deckColumn(String deckPublicId, String column, Class<T> type) {
        return jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM flashcard_deck WHERE public_id = ?",
            type,
            deckPublicId
        );
    }

    private List<TestReviewHistory> reviewHistory(Long deckId) {
        return jdbcTemplate.query(
            """
            SELECT previous_interval_days, new_interval_days
            FROM flashcard_deck_review
            WHERE deck_id = ?
            ORDER BY id ASC
            """,
            (rs, rowNum) -> new TestReviewHistory(
                rs.getInt("previous_interval_days"),
                rs.getInt("new_interval_days")
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

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record TestUser(
        Long id,
        String accessToken
    ) {}

    private record TestReviewResult(
        int status,
        int intervalDays,
        long totalFlashcardsReviewed
    ) {}

    private record TestReviewHistory(
        int previousIntervalDays,
        int newIntervalDays
    ) {}
}

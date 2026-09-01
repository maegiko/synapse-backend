package com.synapse.backend.streak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

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

import com.synapse.backend.flashcards.dto.review.ReviewDeckRequest;
import com.synapse.backend.flashcards.enums.ReviewRating;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class StreakRetrievalIntegrationTest extends PostgresIntegrationTest {

    private static final String DECK_REVIEW_ENDPOINT = "/api/flashcards/{deckId}/review";
    private static final String STREAK_ENDPOINT = "/api/user/streak";
    private static final String VALID_PASSWORD = "password123";
    private static final Instant MIDDAY = Instant.parse("2026-03-10T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-03-10");

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
        setClock(MIDDAY);
    }

    @Test
    void streakIsEmptyWhenTheUserHasNoActivity() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(STREAK_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(0))
            .andExpect(jsonPath("$.longestStreak").value(0))
            .andExpect(jsonPath("$.activeToday").value(false))
            .andExpect(jsonPath("$.lastActiveDate").isEmpty());
    }

    @Test
    void currentStreakCountsConsecutiveDaysEndingToday() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createActivity(user.id(), TODAY, TODAY.minusDays(1), TODAY.minusDays(2));

        mockMvc.perform(get(STREAK_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(3))
            .andExpect(jsonPath("$.longestStreak").value(3))
            .andExpect(jsonPath("$.activeToday").value(true))
            .andExpect(jsonPath("$.lastActiveDate").value(TODAY.toString()));
    }

    @Test
    void longestStreakIsTheLongestRunInHistoryDespiteAGap() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createActivity(
            user.id(),
            TODAY.minusDays(20),
            TODAY.minusDays(19),
            TODAY.minusDays(18),
            TODAY.minusDays(17),
            TODAY.minusDays(16),
            TODAY.minusDays(1),
            TODAY
        );

        mockMvc.perform(get(STREAK_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(2))
            .andExpect(jsonPath("$.longestStreak").value(5))
            .andExpect(jsonPath("$.activeToday").value(true))
            .andExpect(jsonPath("$.lastActiveDate").value(TODAY.toString()));
    }

    @Test
    void currentStreakStaysAliveWhenTheLastActivityWasYesterday() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createActivity(user.id(), TODAY.minusDays(2), TODAY.minusDays(1));

        mockMvc.perform(get(STREAK_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(2))
            .andExpect(jsonPath("$.longestStreak").value(2))
            .andExpect(jsonPath("$.activeToday").value(false))
            .andExpect(jsonPath("$.lastActiveDate").value(TODAY.minusDays(1).toString()));
    }

    @Test
    void currentStreakIsZeroWhenTheLastActivityIsOlderThanYesterday() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createActivity(user.id(), TODAY.minusDays(4), TODAY.minusDays(3), TODAY.minusDays(2));

        mockMvc.perform(get(STREAK_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(0))
            .andExpect(jsonPath("$.longestStreak").value(3))
            .andExpect(jsonPath("$.activeToday").value(false))
            .andExpect(jsonPath("$.lastActiveDate").value(TODAY.minusDays(2).toString()));
    }

    @Test
    void streakOnlyCountsActivityOwnedByTheAuthenticatedUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        createActivity(currentUser.id(), TODAY);
        createActivity(otherUser.id(), TODAY, TODAY.minusDays(1), TODAY.minusDays(2));

        mockMvc.perform(get(STREAK_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(1))
            .andExpect(jsonPath("$.longestStreak").value(1))
            .andExpect(jsonPath("$.activeToday").value(true));

        mockMvc.perform(get(STREAK_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherUser.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(3))
            .andExpect(jsonPath("$.longestStreak").value(3))
            .andExpect(jsonPath("$.activeToday").value(true));
    }

    @Test
    void activityDayFollowsTheUtcDayOfTheBackendClock() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeck(user.id(), "deckutc001", "Systems deck");
        setClock(Instant.parse("2026-03-10T23:59:00Z"));

        reviewDeck(user, "deckutc001");

        assertEquals(List.of(TODAY), activityDates(user.id()));

        setClock(Instant.parse("2026-03-11T00:01:00Z"));

        mockMvc.perform(get(STREAK_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(1))
            .andExpect(jsonPath("$.longestStreak").value(1))
            .andExpect(jsonPath("$.activeToday").value(false))
            .andExpect(jsonPath("$.lastActiveDate").value(TODAY.toString()));

        reviewDeck(user, "deckutc001");

        assertEquals(List.of(TODAY, TODAY.plusDays(1)), activityDates(user.id()));

        mockMvc.perform(get(STREAK_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(2))
            .andExpect(jsonPath("$.longestStreak").value(2))
            .andExpect(jsonPath("$.activeToday").value(true))
            .andExpect(jsonPath("$.lastActiveDate").value(TODAY.plusDays(1).toString()));
    }

    @Test
    void streakReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get(STREAK_ENDPOINT))
            .andExpect(status().isUnauthorized());
    }

    private void setClock(Instant now) {
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private void reviewDeck(TestUser user, String deckId) throws Exception {
        mockMvc.perform(post(DECK_REVIEW_ENDPOINT, deckId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReviewDeckRequest(ReviewRating.GOOD)))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());
    }

    private TestUser register(String fullName, String email) throws Exception {
        String accessToken = registerAndAuthenticate(fullName, email, VALID_PASSWORD);
        Long userId = Long.valueOf(jwtDecoder.decode(accessToken).getSubject());

        return new TestUser(userId, accessToken);
    }

    private void createActivity(Long userId, LocalDate... activityDates) {
        for (LocalDate activityDate : activityDates) {
            jdbcTemplate.update(
                "INSERT INTO streak_activity (user_id, activity_date) VALUES (?, ?)",
                userId,
                activityDate
            );
        }
    }

    private void createDeck(Long userId, String publicId, String title) {
        Long deckId = jdbcTemplate.queryForObject(
            """
            INSERT INTO flashcard_deck (user_id, title, source_type, public_id)
            VALUES (?, ?, 'NOTE', ?)
            RETURNING id
            """,
            Long.class,
            userId,
            title,
            publicId
        );

        jdbcTemplate.update(
            """
            INSERT INTO flashcard (deck_id, question, answer, position, public_id)
            VALUES (?, 'What is a cell?', 'The basic unit of life.', 0, ?)
            """,
            deckId,
            publicId.substring(4) + "c0"
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

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record TestUser(
        Long id,
        String accessToken
    ) {}
}

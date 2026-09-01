package com.synapse.backend.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.support.PostgresIntegrationTest;

/**
 * Study analytics over a window of the user's own calendar days.
 *
 * <p>The clock is pinned late in the UTC day, so a Sydney user is already on the following
 * local date while a UTC user is not. That is where user-local grouping and UTC grouping
 * disagree, and it is what most of the boundary cases here turn on.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsIntegrationTest extends PostgresIntegrationTest {

    private static final String ANALYTICS_ENDPOINT = "/api/user/analytics";
    private static final String DECK_REVIEW_ENDPOINT = "/api/flashcards/{deckId}/review";
    private static final String QUIZ_SCORE_ENDPOINT = "/api/quiz/{quizId}/score";
    private static final String VALID_PASSWORD = "password123";
    private static final String SYDNEY = "Australia/Sydney";
    private static final String UTC = "UTC";

    /** 23:00 UTC on 10 March 2026, which is already 10:00 on the 11th in Sydney. */
    private static final Instant LATE_ON_THE_TENTH = Instant.parse("2026-03-10T23:00:00Z");
    private static final LocalDate UTC_TODAY = LocalDate.parse("2026-03-10");
    private static final LocalDate SYDNEY_TODAY = LocalDate.parse("2026-03-11");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void resetDatabaseAndClock() {
        jdbcTemplate.execute("DELETE FROM app_user");
        when(clock.instant()).thenReturn(LATE_ON_THE_TENTH);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    // --- Period ----------------------------------------------------------------------

    @Test
    void analyticsIsEmptyForAUserWhoHasStudiedNothing() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);

        analytics(user, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.period.days").value(30))
            .andExpect(jsonPath("$.period.from").value("2026-02-09"))
            .andExpect(jsonPath("$.period.to").value("2026-03-10"))
            .andExpect(jsonPath("$.overview.totalStudySeconds").value(0))
            .andExpect(jsonPath("$.overview.activeDays").value(0))
            .andExpect(jsonPath("$.overview.inactiveDays").value(30))
            .andExpect(jsonPath("$.overview.averageSecondsPerActiveDay").value(0))
            .andExpect(jsonPath("$.overview.cardsReviewed").value(0))
            .andExpect(jsonPath("$.overview.lifetimeCardsReviewed").value(0))
            .andExpect(jsonPath("$.overview.deckReviewSessions").value(0))
            .andExpect(jsonPath("$.overview.quizAttempts").value(0))
            .andExpect(jsonPath("$.overview.averageQuizPercentage").isEmpty())
            .andExpect(jsonPath("$.flashcards.retentionRate").isEmpty())
            .andExpect(jsonPath("$.flashcards.perDay").isEmpty())
            .andExpect(jsonPath("$.flashcards.ratings.again").value(0))
            .andExpect(jsonPath("$.flashcards.overdueDecks").value(0))
            .andExpect(jsonPath("$.flashcards.dueTodayDecks").value(0))
            .andExpect(jsonPath("$.flashcards.dueForecast.length()").value(7))
            .andExpect(jsonPath("$.flashcards.mastery.struggling").value(0))
            .andExpect(jsonPath("$.quizzes.attempts").value(0))
            .andExpect(jsonPath("$.quizzes.scoreHistory").isEmpty())
            .andExpect(jsonPath("$.quizzes.averagePercentage").isEmpty())
            .andExpect(jsonPath("$.quizzes.improvement").isEmpty())
            .andExpect(jsonPath("$.consistency.currentStreak").value(0))
            .andExpect(jsonPath("$.consistency.longestInactivityGap").value(30))
            .andExpect(jsonPath("$.dailyActivity.length()").value(30))
            .andExpect(jsonPath("$.dailyActivity[0].date").value("2026-02-09"))
            .andExpect(jsonPath("$.dailyActivity[29].date").value("2026-03-10"));
    }

    @Test
    void analyticsSupportsEveryOfferedPeriod() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.period.days").value(7))
            .andExpect(jsonPath("$.period.from").value("2026-03-04"))
            .andExpect(jsonPath("$.dailyActivity.length()").value(7));

        analytics(user, 30)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.period.from").value("2026-02-09"))
            .andExpect(jsonPath("$.dailyActivity.length()").value(30));

        analytics(user, 90)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.period.from").value("2025-12-11"))
            .andExpect(jsonPath("$.dailyActivity.length()").value(90));

        analytics(user, 365)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.period.from").value("2025-03-11"))
            .andExpect(jsonPath("$.dailyActivity.length()").value(365));
    }

    @Test
    void analyticsRejectsAPeriodThatIsNotOffered() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);

        analytics(user, 45)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("period: must be one of 7, 30, 90, or 365"));

        analytics(user, 0).andExpect(status().isBadRequest());
        analytics(user, -7).andExpect(status().isBadRequest());
        analytics(user, 366).andExpect(status().isBadRequest());
    }

    @Test
    void analyticsWindowEndsOnTheUsersOwnToday() throws Exception {
        TestUser sydney = register("Kenneth", "kenneth@example.com", SYDNEY);
        TestUser utc = register("Ada", "ada@example.com", UTC);

        analytics(sydney, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.period.from").value("2026-03-05"))
            .andExpect(jsonPath("$.period.to").value(SYDNEY_TODAY.toString()));

        analytics(utc, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.period.from").value("2026-03-04"))
            .andExpect(jsonPath("$.period.to").value(UTC_TODAY.toString()));
    }

    @Test
    void analyticsRequiresAuthentication() throws Exception {
        mockMvc.perform(get(ANALYTICS_ENDPOINT)).andExpect(status().isUnauthorized());

        mockMvc.perform(get(ANALYTICS_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }

    // --- Daily aggregation and time zone boundaries ----------------------------------

    @Test
    void aReviewIsCountedOnTheLocalDayTheUserExperiencedIt() throws Exception {
        TestUser sydney = register("Kenneth", "kenneth@example.com", SYDNEY);
        TestUser utc = register("Ada", "ada@example.com", UTC);
        Long sydneyDeck = createDeckWithCards(sydney.id(), "andeck0001", 5);
        Long utcDeck = createDeckWithCards(utc.id(), "andeck0002", 5);

        // The same instant, late in the UTC day: the 11th in Sydney, the 10th in UTC.
        insertReview(sydneyDeck, "GOOD", 5, "2026-03-10T22:30:00", 300);
        insertReview(utcDeck, "GOOD", 5, "2026-03-10T22:30:00", 300);

        analytics(sydney, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flashcards.perDay.length()").value(1))
            .andExpect(jsonPath("$.flashcards.perDay[0].date").value("2026-03-11"))
            .andExpect(jsonPath("$.dailyActivity[6].date").value("2026-03-11"))
            .andExpect(jsonPath("$.dailyActivity[6].cardsReviewed").value(5));

        analytics(utc, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flashcards.perDay[0].date").value("2026-03-10"))
            .andExpect(jsonPath("$.dailyActivity[6].date").value("2026-03-10"))
            .andExpect(jsonPath("$.dailyActivity[6].cardsReviewed").value(5));
    }

    @Test
    void dailyActivitySumsSessionsCardsAndStudyTimeForOneDay() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        Long deckId = createDeckWithCards(user.id(), "andeck0003", 4);
        Long quizId = createQuiz(user.id(), "anquiz0001", "Systems", 10);

        insertReview(deckId, "GOOD", 4, "2026-03-10T08:00:00", 120);
        insertReview(deckId, "EASY", 4, "2026-03-10T09:00:00", 180);
        insertScore(quizId, user.id(), "anscor0001", 8, 10, "2026-03-10T10:00:00", 240);

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyActivity[6].date").value("2026-03-10"))
            .andExpect(jsonPath("$.dailyActivity[6].studySeconds").value(540))
            .andExpect(jsonPath("$.dailyActivity[6].cardsReviewed").value(8))
            .andExpect(jsonPath("$.dailyActivity[6].deckReviews").value(2))
            .andExpect(jsonPath("$.dailyActivity[6].quizAttempts").value(1))
            .andExpect(jsonPath("$.overview.totalStudySeconds").value(540))
            .andExpect(jsonPath("$.overview.cardsReviewed").value(8))
            .andExpect(jsonPath("$.overview.deckReviewSessions").value(2))
            .andExpect(jsonPath("$.overview.quizAttempts").value(1))
            .andExpect(jsonPath("$.overview.activeDays").value(1))
            .andExpect(jsonPath("$.overview.inactiveDays").value(6))
            .andExpect(jsonPath("$.overview.averageSecondsPerActiveDay").value(540));
    }

    @Test
    void activityOutsideTheWindowIsNotCounted() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        Long deckId = createDeckWithCards(user.id(), "andeck0004", 3);

        insertReview(deckId, "GOOD", 3, "2026-03-10T08:00:00", 60);
        // A fortnight earlier: inside the 30 day window, outside the 7 day one.
        insertReview(deckId, "GOOD", 3, "2026-02-24T08:00:00", 60);

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overview.deckReviewSessions").value(1))
            .andExpect(jsonPath("$.overview.cardsReviewed").value(3));

        analytics(user, 30)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overview.deckReviewSessions").value(2))
            .andExpect(jsonPath("$.overview.cardsReviewed").value(6));
    }

    @Test
    void analyticsOnlyCountsTheAuthenticatedUsersOwnStudy() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        TestUser other = register("Ada", "ada@example.com", UTC);
        Long ownDeck = createDeckWithCards(user.id(), "andeck0005", 2);
        Long otherDeck = createDeckWithCards(other.id(), "andeck0006", 9);
        Long otherQuiz = createQuiz(other.id(), "anquiz0002", "Theirs", 10);

        insertReview(ownDeck, "GOOD", 2, "2026-03-10T08:00:00", 100);
        insertReview(otherDeck, "AGAIN", 9, "2026-03-10T08:00:00", 900);
        insertScore(otherQuiz, other.id(), "anscor0002", 10, 10, "2026-03-10T08:00:00", 900);
        scheduleDeck(otherDeck, "AGAIN", 0, LocalDate.parse("2026-03-01"));

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overview.cardsReviewed").value(2))
            .andExpect(jsonPath("$.overview.totalStudySeconds").value(100))
            .andExpect(jsonPath("$.overview.quizAttempts").value(0))
            .andExpect(jsonPath("$.flashcards.ratings.again").value(0))
            .andExpect(jsonPath("$.flashcards.ratings.good").value(1))
            .andExpect(jsonPath("$.flashcards.overdueDecks").value(0))
            .andExpect(jsonPath("$.flashcards.mastery.struggling").value(0));
    }

    // --- Ratings, retention, mastery, forecast ---------------------------------------

    @Test
    void retentionRateIsTheShareOfGoodAndEasyRatings() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        Long deckId = createDeckWithCards(user.id(), "andeck0007", 1);

        insertReview(deckId, "AGAIN", 1, "2026-03-08T08:00:00", 60);
        insertReview(deckId, "HARD", 1, "2026-03-09T08:00:00", 60);
        insertReview(deckId, "GOOD", 1, "2026-03-10T08:00:00", 60);
        insertReview(deckId, "GOOD", 1, "2026-03-10T09:00:00", 60);
        insertReview(deckId, "EASY", 1, "2026-03-10T10:00:00", 60);
        insertReview(deckId, "EASY", 1, "2026-03-10T11:00:00", 60);

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flashcards.ratings.again").value(1))
            .andExpect(jsonPath("$.flashcards.ratings.hard").value(1))
            .andExpect(jsonPath("$.flashcards.ratings.good").value(2))
            .andExpect(jsonPath("$.flashcards.ratings.easy").value(2))
            .andExpect(jsonPath("$.flashcards.retentionRate").value(4.0 / 6))
            .andExpect(jsonPath("$.flashcards.reviewSessions").value(6));
    }

    @Test
    void retentionRateIsNullWhenNothingWasRatedInTheWindow() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        Long deckId = createDeckWithCards(user.id(), "andeck0008", 1);

        // Outside the seven day window, so the window itself has no ratings to divide.
        insertReview(deckId, "GOOD", 1, "2026-02-20T08:00:00", 60);

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flashcards.retentionRate").isEmpty());
    }

    @Test
    void masteryBucketsDecksByLatestRatingAndInterval() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        scheduleDeck(createDeckWithCards(user.id(), "anmast0001", 1), "AGAIN", 0, UTC_TODAY);
        scheduleDeck(createDeckWithCards(user.id(), "anmast0002", 1), "HARD", 2, UTC_TODAY);
        scheduleDeck(createDeckWithCards(user.id(), "anmast0003", 1), "GOOD", 20, UTC_TODAY);
        scheduleDeck(createDeckWithCards(user.id(), "anmast0004", 1), "EASY", 21, UTC_TODAY);
        scheduleDeck(createDeckWithCards(user.id(), "anmast0005", 1), "GOOD", 60, UTC_TODAY);
        // Never reviewed, so it has no latest rating and belongs to no bucket.
        createDeckWithCards(user.id(), "anmast0006", 1);

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flashcards.mastery.struggling").value(2))
            .andExpect(jsonPath("$.flashcards.mastery.learning").value(1))
            .andExpect(jsonPath("$.flashcards.mastery.strong").value(2));
    }

    @Test
    void dueCountsAndForecastAreTakenFromTheUsersToday() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        scheduleDeck(createDeckWithCards(user.id(), "andue00001", 1), "GOOD", 1, LocalDate.parse("2026-03-08"));
        scheduleDeck(createDeckWithCards(user.id(), "andue00002", 1), "GOOD", 1, LocalDate.parse("2026-03-09"));
        scheduleDeck(createDeckWithCards(user.id(), "andue00003", 1), "GOOD", 1, UTC_TODAY);
        scheduleDeck(createDeckWithCards(user.id(), "andue00004", 1), "GOOD", 2, LocalDate.parse("2026-03-12"));
        scheduleDeck(createDeckWithCards(user.id(), "andue00005", 1), "GOOD", 2, LocalDate.parse("2026-03-12"));
        // Beyond the seven day forecast, so it is scheduled but not listed.
        scheduleDeck(createDeckWithCards(user.id(), "andue00006", 1), "GOOD", 30, LocalDate.parse("2026-04-10"));

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flashcards.overdueDecks").value(2))
            .andExpect(jsonPath("$.flashcards.dueTodayDecks").value(1))
            .andExpect(jsonPath("$.flashcards.dueForecast.length()").value(7))
            .andExpect(jsonPath("$.flashcards.dueForecast[0].date").value("2026-03-10"))
            .andExpect(jsonPath("$.flashcards.dueForecast[0].deckCount").value(1))
            .andExpect(jsonPath("$.flashcards.dueForecast[1].deckCount").value(0))
            .andExpect(jsonPath("$.flashcards.dueForecast[2].date").value("2026-03-12"))
            .andExpect(jsonPath("$.flashcards.dueForecast[2].deckCount").value(2))
            .andExpect(jsonPath("$.flashcards.dueForecast[6].date").value("2026-03-16"))
            .andExpect(jsonPath("$.flashcards.dueForecast[6].deckCount").value(0));
    }

    @Test
    void dueCountsFollowTheUsersOwnTodayAcrossTheUtcBoundary() throws Exception {
        TestUser sydney = register("Kenneth", "kenneth@example.com", SYDNEY);
        // Due on the 11th: already today in Sydney while UTC is still on the 10th.
        scheduleDeck(createDeckWithCards(sydney.id(), "andue00007", 1), "GOOD", 1, SYDNEY_TODAY);

        analytics(sydney, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flashcards.dueTodayDecks").value(1))
            .andExpect(jsonPath("$.flashcards.overdueDecks").value(0))
            .andExpect(jsonPath("$.flashcards.dueForecast[0].date").value(SYDNEY_TODAY.toString()));
    }

    // --- Quizzes ---------------------------------------------------------------------

    @Test
    void quizFiguresAverageAndRankThePeriodsAttempts() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        Long first = createQuiz(user.id(), "anquiz0003", "Cells", 10);
        Long second = createQuiz(user.id(), "anquiz0004", "Enzymes", 10);

        insertScore(first, user.id(), "anscor0003", 6, 10, "2026-03-08T08:00:00", 100);
        insertScore(first, user.id(), "anscor0004", 8, 10, "2026-03-09T08:00:00", 200);
        insertScore(second, user.id(), "anscor0005", 10, 10, "2026-03-10T08:00:00", 300);

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes.attempts").value(3))
            .andExpect(jsonPath("$.quizzes.distinctQuizzesAttempted").value(2))
            .andExpect(jsonPath("$.quizzes.averagePercentage").value(80.0))
            .andExpect(jsonPath("$.quizzes.bestPercentage").value(100.0))
            .andExpect(jsonPath("$.quizzes.averageDurationSeconds").value(200.0))
            .andExpect(jsonPath("$.quizzes.perDay.length()").value(3))
            .andExpect(jsonPath("$.quizzes.scoreHistory.length()").value(3))
            .andExpect(jsonPath("$.quizzes.scoreHistory[0].id").value("anscor0003"))
            .andExpect(jsonPath("$.quizzes.scoreHistory[0].quizId").value("anquiz0003"))
            .andExpect(jsonPath("$.quizzes.scoreHistory[0].quizTitle").value("Cells"))
            .andExpect(jsonPath("$.quizzes.scoreHistory[0].score").value(6))
            .andExpect(jsonPath("$.quizzes.scoreHistory[0].totalQuestions").value(10))
            .andExpect(jsonPath("$.quizzes.scoreHistory[0].percentage").value(60.0))
            .andExpect(jsonPath("$.quizzes.scoreHistory[0].durationSeconds").value(100))
            .andExpect(jsonPath("$.quizzes.scoreHistory[2].id").value("anscor0005"))
            .andExpect(jsonPath("$.overview.averageQuizPercentage").value(80.0));
    }

    @Test
    void improvementComparesTheFirstAndLatestAttemptOfRepeatedQuizzes() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        Long repeated = createQuiz(user.id(), "anquiz0005", "Cells", 10);
        Long onceOnly = createQuiz(user.id(), "anquiz0006", "Enzymes", 10);

        insertScore(repeated, user.id(), "anscor0006", 4, 10, "2026-03-08T08:00:00", null);
        insertScore(repeated, user.id(), "anscor0007", 6, 10, "2026-03-09T08:00:00", null);
        insertScore(repeated, user.id(), "anscor0008", 9, 10, "2026-03-10T08:00:00", null);
        // A single attempt shows no trend, so it is left out of the average.
        insertScore(onceOnly, user.id(), "anscor0009", 1, 10, "2026-03-10T09:00:00", null);

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes.improvement").value(50.0));
    }

    @Test
    void improvementIsNullWhenNoQuizWasAttemptedTwice() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        Long quizId = createQuiz(user.id(), "anquiz0007", "Cells", 10);

        insertScore(quizId, user.id(), "anscor0010", 7, 10, "2026-03-10T08:00:00", null);

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes.attempts").value(1))
            .andExpect(jsonPath("$.quizzes.improvement").isEmpty());
    }

    // --- Consistency -----------------------------------------------------------------

    @Test
    void consistencyReportsActiveDaysSessionsAndTheLongestGap() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        Long deckId = createDeckWithCards(user.id(), "andeck0009", 1);
        Long quizId = createQuiz(user.id(), "anquiz0008", "Cells", 10);

        // Active on the 4th and the 10th, leaving a five day gap between them.
        insertReview(deckId, "GOOD", 1, "2026-03-04T08:00:00", 60);
        insertReview(deckId, "GOOD", 1, "2026-03-10T08:00:00", 60);
        insertScore(quizId, user.id(), "anscor0011", 5, 10, "2026-03-10T09:00:00", 60);

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consistency.activeDays").value(2))
            .andExpect(jsonPath("$.consistency.inactiveDays").value(5))
            .andExpect(jsonPath("$.consistency.averageSessionsPerActiveDay").value(1.5))
            .andExpect(jsonPath("$.consistency.longestInactivityGap").value(5));
    }

    // --- Submitted durations ---------------------------------------------------------

    @Test
    void reviewStoresAnOptionalDurationAndCountsItAsStudyTime() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        createDeckWithCards(user.id(), "andur00001", 3);

        mockMvc.perform(post(DECK_REVIEW_ENDPOINT, "andur00001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"GOOD\",\"durationSeconds\":450}")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        assertEquals(450, reviewDuration("andur00001"));

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overview.totalStudySeconds").value(450));
    }

    @Test
    void reviewWithoutADurationIsSavedAndCountedAsUntimed() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        createDeckWithCards(user.id(), "andur00002", 3);

        mockMvc.perform(post(DECK_REVIEW_ENDPOINT, "andur00002")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"GOOD\"}")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        assertNull(reviewDuration("andur00002"));

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overview.deckReviewSessions").value(1))
            .andExpect(jsonPath("$.overview.cardsReviewed").value(3))
            .andExpect(jsonPath("$.overview.totalStudySeconds").value(0));
    }

    @Test
    void reviewRejectsADurationOutsideTheAllowedRange() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        createDeckWithCards(user.id(), "andur00003", 3);

        mockMvc.perform(post(DECK_REVIEW_ENDPOINT, "andur00003")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"GOOD\",\"durationSeconds\":-1}")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("durationSeconds: must be greater than or equal to 0"));

        mockMvc.perform(post(DECK_REVIEW_ENDPOINT, "andur00003")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"GOOD\",\"durationSeconds\":21601}")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("durationSeconds: must be less than or equal to 21600"));

        assertEquals(0, countReviews("andur00003"));
    }

    @Test
    void scoreStoresAnOptionalDurationAndCountsItAsStudyTime() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        createQuiz(user.id(), "andurq0001", "Cells", 10);

        mockMvc.perform(post(QUIZ_SCORE_ENDPOINT, "andurq0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":7,\"durationSeconds\":600}")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        assertEquals(600, scoreDuration("andurq0001"));

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overview.totalStudySeconds").value(600))
            .andExpect(jsonPath("$.quizzes.averageDurationSeconds").value(600.0));
    }

    @Test
    void scoreWithoutADurationIsSavedAndCountedAsUntimed() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        createQuiz(user.id(), "andurq0002", "Cells", 10);

        mockMvc.perform(post(QUIZ_SCORE_ENDPOINT, "andurq0002")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":7}")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        assertNull(scoreDuration("andurq0002"));

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes.attempts").value(1))
            .andExpect(jsonPath("$.overview.totalStudySeconds").value(0))
            .andExpect(jsonPath("$.quizzes.averageDurationSeconds").isEmpty());
    }

    @Test
    void scoreRejectsADurationOutsideTheAllowedRange() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        createQuiz(user.id(), "andurq0003", "Cells", 10);

        mockMvc.perform(post(QUIZ_SCORE_ENDPOINT, "andurq0003")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":7,\"durationSeconds\":21601}")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("durationSeconds: must be less than or equal to 21600"));

        assertEquals(0, countScores("andurq0003"));
    }

    @Test
    void rowsSavedBeforeDurationsExistedAreCountedButNotTimed() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", UTC);
        Long deckId = createDeckWithCards(user.id(), "andeck0010", 2);
        Long quizId = createQuiz(user.id(), "anquiz0009", "Cells", 10);

        // Historical rows: saved when no duration was recorded at all.
        insertReview(deckId, "GOOD", 2, "2026-03-09T08:00:00", null);
        insertScore(quizId, user.id(), "anscor0012", 5, 10, "2026-03-09T09:00:00", null);
        // A newer pair that does report its time.
        insertReview(deckId, "GOOD", 2, "2026-03-10T08:00:00", 200);
        insertScore(quizId, user.id(), "anscor0013", 7, 10, "2026-03-10T09:00:00", 400);

        analytics(user, 7)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overview.deckReviewSessions").value(2))
            .andExpect(jsonPath("$.overview.cardsReviewed").value(4))
            .andExpect(jsonPath("$.overview.quizAttempts").value(2))
            // Only the timed rows contribute seconds; the untimed ones still count as activity.
            .andExpect(jsonPath("$.overview.totalStudySeconds").value(600))
            .andExpect(jsonPath("$.overview.activeDays").value(2))
            .andExpect(jsonPath("$.overview.averageSecondsPerActiveDay").value(300))
            .andExpect(jsonPath("$.dailyActivity[5].studySeconds").value(0))
            .andExpect(jsonPath("$.dailyActivity[5].deckReviews").value(1))
            .andExpect(jsonPath("$.dailyActivity[6].studySeconds").value(600))
            // The average skips the untimed attempt rather than treating it as zero.
            .andExpect(jsonPath("$.quizzes.averageDurationSeconds").value(400.0))
            .andExpect(jsonPath("$.quizzes.scoreHistory[0].durationSeconds").isEmpty());
    }

    // --- Helpers ---------------------------------------------------------------------

    private ResultActions analytics(TestUser user, Integer period) throws Exception {
        var request = get(ANALYTICS_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()));

        if (period != null)
            request = request.param("period", String.valueOf(period));

        return mockMvc.perform(request);
    }

    private TestUser register(String fullName, String email, String timeZone) throws Exception {
        String accessToken = registerAndAuthenticate(fullName, email, VALID_PASSWORD, timeZone);
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

    /** Puts a deck in the state a past review would have left its schedule in. */
    private void scheduleDeck(Long deckId, String lastRating, int intervalDays, LocalDate nextReviewDate) {
        jdbcTemplate.update(
            """
            UPDATE flashcard_deck
            SET review_count = 1, last_rating = ?, interval_days = ?, next_review_date = ?
            WHERE id = ?
            """,
            lastRating,
            intervalDays,
            nextReviewDate,
            deckId
        );
    }

    /** A review row at an exact UTC instant, with or without a recorded duration. */
    private void insertReview(
        Long deckId,
        String rating,
        int cardsReviewed,
        String reviewedAtUtc,
        Integer durationSeconds
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO flashcard_deck_review (
                deck_id, rating, cards_reviewed, total_cards,
                previous_interval_days, new_interval_days,
                previous_ease_factor, new_ease_factor, reviewed_at, duration_seconds
            )
            VALUES (?, ?, ?, ?, 0, 1, 2.50, 2.50, ?::timestamp, ?)
            """,
            deckId,
            rating,
            cardsReviewed,
            cardsReviewed,
            reviewedAtUtc,
            durationSeconds
        );
    }

    private Long createQuiz(Long userId, String publicId, String title, int numberOfQuestions) {
        Long quizId = jdbcTemplate.queryForObject(
            """
            INSERT INTO quiz (user_id, public_id, title, description, source_type)
            VALUES (?, ?, ?, 'A quiz', 'MANUAL')
            RETURNING id
            """,
            Long.class,
            userId,
            publicId,
            title
        );

        for (int position = 0; position < numberOfQuestions; position++) {
            jdbcTemplate.update(
                """
                INSERT INTO quiz_question (quiz_id, public_id, question_text, question_type, position)
                VALUES (?, ?, ?, 'BOOLEAN', ?)
                """,
                quizId,
                publicId.substring(2) + "q" + position,
                "Question " + position,
                position
            );
        }

        return quizId;
    }

    /** A saved score at an exact UTC instant, with or without a recorded duration. */
    private void insertScore(
        Long quizId,
        Long userId,
        String publicId,
        int score,
        int totalQuestions,
        String createdAtUtc,
        Integer durationSeconds
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO quiz_score (quiz_id, user_id, public_id, score, total_questions, created_at, duration_seconds)
            VALUES (?, ?, ?, ?, ?, ?::timestamp, ?)
            """,
            quizId,
            userId,
            publicId,
            score,
            totalQuestions,
            createdAtUtc,
            durationSeconds
        );
    }

    private Integer reviewDuration(String deckPublicId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT r.duration_seconds
            FROM flashcard_deck_review r
            JOIN flashcard_deck d ON d.id = r.deck_id
            WHERE d.public_id = ?
            """,
            Integer.class,
            deckPublicId
        );
    }

    private int countReviews(String deckPublicId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM flashcard_deck_review r
            JOIN flashcard_deck d ON d.id = r.deck_id
            WHERE d.public_id = ?
            """,
            Integer.class,
            deckPublicId
        );
    }

    private Integer scoreDuration(String quizPublicId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT s.duration_seconds
            FROM quiz_score s
            JOIN quiz q ON q.id = s.quiz_id
            WHERE q.public_id = ?
            """,
            Integer.class,
            quizPublicId
        );
    }

    private int countScores(String quizPublicId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM quiz_score s
            JOIN quiz q ON q.id = s.quiz_id
            WHERE q.public_id = ?
            """,
            Integer.class,
            quizPublicId
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

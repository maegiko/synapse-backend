package com.synapse.backend.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.groups.entities.StudyGroup;
import com.synapse.backend.groups.repositories.StudyGroupRepository;
import com.synapse.backend.support.PostgresIntegrationTest;
import com.synapse.backend.user.dto.UpdateUserDetailsRequest;

import tools.jackson.databind.ObjectMapper;

/**
 * Calendar-day behaviour in the user's own time zone: which day a streak lands on, which
 * day a deck is due, and that the instants behind both stay UTC.
 *
 * <p>Every case pins the clock to an instant that is late in the UTC day, so a user east of
 * UTC is already on the following local date. That is where a UTC-only implementation and a
 * user-local one disagree, and it is the only way to tell them apart.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserTimeZoneIntegrationTest extends PostgresIntegrationTest {

    private static final String USER_DETAILS_ENDPOINT = "/api/user/details";
    private static final String STREAK_ENDPOINT = "/api/user/streak";
    private static final String DECK_REVIEW_ENDPOINT = "/api/flashcards/{deckId}/review";
    private static final String REVIEW_QUEUE_ENDPOINT = "/api/flashcards/review";
    private static final String QUIZ_SCORE_ENDPOINT = "/api/quiz/{quizId}/score";
    private static final String VALID_PASSWORD = "password123";
    private static final String SYDNEY = "Australia/Sydney";
    private static final String MIGRATION_FILE = "db/migration/V21__add_user_time_zone.sql";
    private static final String UTC_TIMESTAMP_MIGRATION_FILE =
        "db/migration/V22__normalise_generated_timestamps_to_utc.sql";

    /**
     * 10 March 2026 at 23:00 UTC. Sydney is on daylight saving at UTC+11, so it is already
     * 11 March there while UTC is still on the 10th.
     */
    private static final Instant LATE_ON_THE_TENTH = Instant.parse("2026-03-10T23:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private StudyGroupRepository studyGroupRepository;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void resetDatabaseAndClock() {
        jdbcTemplate.execute("DELETE FROM app_user");
        setClock(LATE_ON_THE_TENTH);
    }

    // --- Migration -------------------------------------------------------------------

    @Test
    void migrationBackfillsExistingUsersWithSydneyAndRedatesTheirScheduledDecks() {
        // A user as they looked before the migration: no zone of their own, and a deck dated
        // from the UTC day of its last review rather than the owner's local day.
        Long userId = createUserRow("Kenneth", "kenneth@example.com");
        Long scheduledDeckId = createDeckRow(userId, "tzmigrate1", "Reviewed deck");
        markReviewed(scheduledDeckId, LocalDateTime.parse("2026-03-10T23:30:00"), 3, LocalDate.parse("2026-03-13"));
        Long unreviewedDeckId = createDeckRow(userId, "tzmigrate2", "Never reviewed deck");

        runMigrationBackfill();

        assertEquals(SYDNEY, timeZoneOf("kenneth@example.com"));
        // 23:30 UTC on the 10th is 10:30 on the 11th in Sydney, so three days on is the 14th.
        assertEquals(LocalDate.parse("2026-03-14"), deckColumn(scheduledDeckId, "next_review_date", LocalDate.class));
        assertNull(deckColumn(unreviewedDeckId, "next_review_date", LocalDate.class));
    }

    @Test
    void migrationLeavesTheLastReviewInstantAlone() {
        Long userId = createUserRow("Kenneth", "kenneth@example.com");
        Long deckId = createDeckRow(userId, "tzmigrate3", "Reviewed deck");
        LocalDateTime reviewedAt = LocalDateTime.parse("2026-03-10T23:30:00");
        markReviewed(deckId, reviewedAt, 3, LocalDate.parse("2026-03-13"));

        runMigrationBackfill();

        assertEquals(reviewedAt, deckColumn(deckId, "last_reviewed_at", LocalDateTime.class));
    }

    @Test
    void usersInsertedWithoutATimeZoneFallBackToUtc() {
        createUserRow("Kenneth", "kenneth@example.com");

        assertEquals("UTC", timeZoneOf("kenneth@example.com"));
    }

    @Test
    void migrationNormalisesExistingJvmGeneratedTimestampsToUtc() {
        Long userId = createUserRow("Kenneth", "kenneth@example.com");
        Long deckId = createDeckRow(userId, "tzmigrate4", "Existing deck");
        LocalDateTime sydneyTime = LocalDateTime.parse("2026-01-15T10:00:00");

        jdbcTemplate.update(
            "UPDATE flashcard_deck SET created_at = ?, updated_at = ? WHERE id = ?",
            sydneyTime,
            sydneyTime,
            deckId
        );

        runDataStatements(UTC_TIMESTAMP_MIGRATION_FILE);

        LocalDateTime utcTime = LocalDateTime.parse("2026-01-14T23:00:00");
        assertEquals(utcTime, deckColumn(deckId, "created_at", LocalDateTime.class));
        assertEquals(utcTime, deckColumn(deckId, "updated_at", LocalDateTime.class));
    }

    // --- Streak days -----------------------------------------------------------------

    @Test
    void streakActivityIsRecordedOnTheUsersLocalDayNotTheUtcDay() throws Exception {
        TestUser sydney = register("Kenneth", "kenneth@example.com", SYDNEY);
        createDeckWithCards(sydney.id(), "tzstreak01", 1);

        review(sydney, "tzstreak01");

        assertEquals(LocalDate.parse("2026-03-11"), onlyActivityDate(sydney.id()));
    }

    @Test
    void streakActivityForAUtcUserStaysOnTheUtcDay() throws Exception {
        TestUser utc = register("Ada", "ada@example.com", "UTC");
        createDeckWithCards(utc.id(), "tzstreak02", 1);

        review(utc, "tzstreak02");

        assertEquals(LocalDate.parse("2026-03-10"), onlyActivityDate(utc.id()));
    }

    @Test
    void activeTodayFollowsTheUsersOwnMidnight() throws Exception {
        TestUser sydney = register("Kenneth", "kenneth@example.com", SYDNEY);
        createDeckWithCards(sydney.id(), "tzstreak03", 1);
        review(sydney, "tzstreak03");

        mockMvc.perform(get(STREAK_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(sydney.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeToday").value(true))
            .andExpect(jsonPath("$.currentStreak").value(1))
            .andExpect(jsonPath("$.lastActiveDate").value("2026-03-11"));
    }

    @Test
    void changingTimeZoneMovesTodayWithoutRewritingRecordedStreakDays() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", "UTC");
        createDeckWithCards(user.id(), "tzstreak04", 1);
        review(user, "tzstreak04");

        assertEquals(LocalDate.parse("2026-03-10"), onlyActivityDate(user.id()));

        mockMvc.perform(patch(USER_DETAILS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateUserDetailsRequest(null, SYDNEY)))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        // Today is now the 11th in Sydney, so the day already earned on the 10th is yesterday:
        // the run still counts, but it is no longer today, and the stored date is untouched.
        mockMvc.perform(get(STREAK_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeToday").value(false))
            .andExpect(jsonPath("$.currentStreak").value(1))
            .andExpect(jsonPath("$.lastActiveDate").value("2026-03-10"));

        assertEquals(LocalDate.parse("2026-03-10"), onlyActivityDate(user.id()));
    }

    @Test
    void streakDaysOfOneUserAreNotAffectedByAnotherUsersTimeZone() throws Exception {
        TestUser sydney = register("Kenneth", "kenneth@example.com", SYDNEY);
        TestUser utc = register("Ada", "ada@example.com", "UTC");
        createDeckWithCards(sydney.id(), "tzstreak05", 1);
        createDeckWithCards(utc.id(), "tzstreak06", 1);

        review(sydney, "tzstreak05");
        review(utc, "tzstreak06");

        assertEquals(LocalDate.parse("2026-03-11"), onlyActivityDate(sydney.id()));
        assertEquals(LocalDate.parse("2026-03-10"), onlyActivityDate(utc.id()));
    }

    @Test
    void aDaylightSavingChangeMovesTheLocalMidnightWithIt() throws Exception {
        // Sydney leaves daylight saving early on 5 April 2026, dropping from UTC+11 to UTC+10.
        TestUser sydney = register("Kenneth", "kenneth@example.com", SYDNEY);
        createDeckWithCards(sydney.id(), "tzstreak07", 1);

        // 00:30 on the 5th in Sydney, while it is still the 4th in UTC.
        setClock(Instant.parse("2026-04-04T13:30:00Z"));
        review(sydney, "tzstreak07");

        assertEquals(LocalDate.parse("2026-04-05"), onlyActivityDate(sydney.id()));

        // A full day later the offset is UTC+10, so it is 23:30 on the 5th and still the same
        // local day. A frozen UTC+11 offset would have rolled over to the 6th by now.
        setClock(Instant.parse("2026-04-05T13:30:00Z"));

        mockMvc.perform(get(STREAK_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(sydney.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeToday").value(true))
            .andExpect(jsonPath("$.lastActiveDate").value("2026-04-05"));
    }

    // --- Flashcard scheduling --------------------------------------------------------

    @Test
    void reviewSchedulesTheNextDueDateFromTheUsersLocalDay() throws Exception {
        TestUser sydney = register("Kenneth", "kenneth@example.com", SYDNEY);
        Long deckId = createDeckWithCards(sydney.id(), "tzdeck0001", 1);

        review(sydney, "tzdeck0001")
            .andExpect(jsonPath("$.nextReviewDate").value("2026-03-12"));

        // GOOD on a fresh deck is a one day interval, counted from the 11th in Sydney.
        assertEquals(LocalDate.parse("2026-03-12"), deckColumn(deckId, "next_review_date", LocalDate.class));
    }

    @Test
    void reviewSchedulesFromTheUtcDayForAUtcUser() throws Exception {
        TestUser utc = register("Ada", "ada@example.com", "UTC");
        Long deckId = createDeckWithCards(utc.id(), "tzdeck0002", 1);

        review(utc, "tzdeck0002")
            .andExpect(jsonPath("$.nextReviewDate").value("2026-03-11"));

        assertEquals(LocalDate.parse("2026-03-11"), deckColumn(deckId, "next_review_date", LocalDate.class));
    }

    @Test
    void aDeckIsDueOnceItIsTheDueDateWhereTheUserIs() throws Exception {
        TestUser sydney = register("Kenneth", "kenneth@example.com", SYDNEY);
        Long deckId = createDeckWithCards(sydney.id(), "tzdeck0003", 1);
        markReviewed(deckId, LocalDateTime.parse("2026-03-10T09:00:00"), 1, LocalDate.parse("2026-03-11"));

        // It is the 11th in Sydney and still the 10th in UTC, so the deck is due now.
        mockMvc.perform(get(REVIEW_QUEUE_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(sydney.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decks.length()").value(1))
            .andExpect(jsonPath("$.decks[0].deckId").value("tzdeck0003"));
    }

    @Test
    void theSameDeckIsNotYetDueForAUtcUser() throws Exception {
        TestUser utc = register("Ada", "ada@example.com", "UTC");
        Long deckId = createDeckWithCards(utc.id(), "tzdeck0004", 1);
        markReviewed(deckId, LocalDateTime.parse("2026-03-10T09:00:00"), 1, LocalDate.parse("2026-03-11"));

        mockMvc.perform(get(REVIEW_QUEUE_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(utc.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decks.length()").value(0));
    }

    @Test
    void changingTimeZoneBringsADeckDueWithoutTouchingItsStoredDate() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com", "UTC");
        Long deckId = createDeckWithCards(user.id(), "tzdeck0005", 1);
        markReviewed(deckId, LocalDateTime.parse("2026-03-10T09:00:00"), 1, LocalDate.parse("2026-03-11"));

        mockMvc.perform(get(REVIEW_QUEUE_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decks.length()").value(0));

        mockMvc.perform(patch(USER_DETAILS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateUserDetailsRequest(null, SYDNEY)))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        mockMvc.perform(get(REVIEW_QUEUE_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decks.length()").value(1));

        assertEquals(LocalDate.parse("2026-03-11"), deckColumn(deckId, "next_review_date", LocalDate.class));
    }

    // --- Stored instants stay UTC ----------------------------------------------------

    @Test
    void databaseGeneratedTimestampsUseUtcRatherThanTheJvmTimeZone() throws Exception {
        TestUser sydney = register("Kenneth", "kenneth@example.com", SYDNEY);
        LocalDateTime beforeSave = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);

        StudyGroup group = studyGroupRepository.saveAndFlush(new StudyGroup(sydney.id(), "Physics", null));

        LocalDateTime afterSave = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1);
        assertEquals("UTC", jdbcTemplate.queryForObject("SHOW TIME ZONE", String.class));
        assertTrue(!group.getCreatedAt().isBefore(beforeSave));
        assertTrue(!group.getCreatedAt().isAfter(afterSave));
    }

    @Test
    void aReviewStoresItsInstantInUtcNotTheUsersWallClock() throws Exception {
        TestUser sydney = register("Kenneth", "kenneth@example.com", SYDNEY);
        Long deckId = createDeckWithCards(sydney.id(), "tzdeck0006", 1);

        review(sydney, "tzdeck0006");

        LocalDateTime utcInstant = LocalDateTime.parse("2026-03-10T23:00:00");

        assertEquals(utcInstant, deckColumn(deckId, "last_reviewed_at", LocalDateTime.class));
        assertEquals(
            utcInstant,
            jdbcTemplate.queryForObject(
                "SELECT reviewed_at FROM flashcard_deck_review WHERE deck_id = ?",
                LocalDateTime.class,
                deckId
            )
        );
    }

    @Test
    void aQuizScoreStoresItsInstantInUtcFromTheInjectedClock() throws Exception {
        TestUser sydney = register("Kenneth", "kenneth@example.com", SYDNEY);
        createQuizWithOneQuestion(sydney.id(), "tzquiz0001");

        mockMvc.perform(post(QUIZ_SCORE_ENDPOINT, "tzquiz0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":1}")
                .header(HttpHeaders.AUTHORIZATION, bearer(sydney.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdAt").value("2026-03-10T23:00:00"));

        assertEquals(
            LocalDateTime.parse("2026-03-10T23:00:00"),
            jdbcTemplate.queryForObject(
                "SELECT created_at FROM quiz_score WHERE user_id = ?",
                LocalDateTime.class,
                sydney.id()
            )
        );
    }

    // --- Helpers ---------------------------------------------------------------------

    /**
     * Runs the data statements of the time zone migration against rows seeded to look the way
     * they did before it. The schema change itself has already been applied by Flyway, so only
     * the backfill and the redating are replayed, and they are read from the real migration
     * file rather than copied into this test.
     */
    private void runMigrationBackfill() {
        runDataStatements(MIGRATION_FILE);
    }

    private void runDataStatements(String migrationFile) {
        String sql;

        try (InputStream migration = new ClassPathResource(migrationFile).getInputStream()) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (String statement : sql.replaceAll("(?m)^--.*$", "").split(";")) {
            if (statement.strip().toUpperCase(Locale.ROOT).startsWith("UPDATE"))
                jdbcTemplate.execute(statement);
        }
    }

    private void setClock(Instant now) {
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private TestUser register(String fullName, String email, String timeZone) throws Exception {
        String accessToken = registerAndAuthenticate(fullName, email, VALID_PASSWORD, timeZone);
        Long userId = Long.valueOf(jwtDecoder.decode(accessToken).getSubject());

        return new TestUser(userId, accessToken);
    }

    private ResultActions review(TestUser user, String deckId) throws Exception {
        return mockMvc.perform(post(DECK_REVIEW_ENDPOINT, deckId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"GOOD\"}")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());
    }

    private Long createUserRow(String fullName, String email) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO app_user (full_name, email, password_hash)
            VALUES (?, ?, 'hash')
            RETURNING id
            """,
            Long.class,
            fullName,
            email
        );
    }

    private Long createDeckRow(Long userId, String publicId, String title) {
        return jdbcTemplate.queryForObject(
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
    }

    private Long createDeckWithCards(Long userId, String publicId, int numberOfCards) {
        Long deckId = createDeckRow(userId, publicId, "Systems deck");

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

    /** Puts a deck in the state a past review would have left it in. */
    private void markReviewed(Long deckId, LocalDateTime lastReviewedAt, int intervalDays, LocalDate nextReviewDate) {
        jdbcTemplate.update(
            """
            UPDATE flashcard_deck
            SET review_count = 1,
                interval_days = ?,
                last_rating = 'GOOD',
                last_reviewed_at = ?,
                next_review_date = ?
            WHERE id = ?
            """,
            intervalDays,
            lastReviewedAt,
            nextReviewDate,
            deckId
        );
    }

    private void createQuizWithOneQuestion(Long userId, String publicId) {
        Long quizId = jdbcTemplate.queryForObject(
            """
            INSERT INTO quiz (user_id, public_id, title, description, source_type)
            VALUES (?, ?, 'Systems quiz', 'One question', 'MANUAL')
            RETURNING id
            """,
            Long.class,
            userId,
            publicId
        );

        jdbcTemplate.update(
            """
            INSERT INTO quiz_question (quiz_id, public_id, question_text, question_type, position)
            VALUES (?, ?, 'What is a stock?', 'BOOLEAN', 0)
            """,
            quizId,
            publicId.substring(2) + "q1"
        );
    }

    private String timeZoneOf(String email) {
        return jdbcTemplate.queryForObject(
            "SELECT time_zone FROM app_user WHERE email = ?",
            String.class,
            email
        );
    }

    private LocalDate onlyActivityDate(Long userId) {
        return jdbcTemplate.queryForObject(
            "SELECT activity_date FROM streak_activity WHERE user_id = ?",
            LocalDate.class,
            userId
        );
    }

    private <T> T deckColumn(Long deckId, String column, Class<T> type) {
        return jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM flashcard_deck WHERE id = ?",
            type,
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

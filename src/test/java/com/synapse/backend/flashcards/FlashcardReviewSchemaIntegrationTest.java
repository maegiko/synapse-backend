package com.synapse.backend.flashcards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.flashcards.dto.generate.FlashcardGenerateNoteRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class FlashcardReviewSchemaIntegrationTest extends PostgresIntegrationTest {

    private static final String GENERATE_ENDPOINT = "/api/flashcards/generate";
    private static final String REVIEW_ENDPOINT = "/api/flashcards/{deckId}/review";
    private static final String DECK_ENDPOINT = "/api/flashcards/{deckId}";
    private static final String VALID_PASSWORD = "password123";
    private static final Instant MIDDAY = Instant.parse("2026-03-10T12:00:00Z");

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
    void resetDatabaseClockAndMocks() {
        jdbcTemplate.execute("DELETE FROM app_user");
        reset(llmClient);
        when(clock.instant()).thenReturn(MIDDAY);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void generatedDecksStartUnreviewedAndUnscheduled() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn(validFlashcardJson());
        TestUser user = register("Kenneth", "kenneth@example.com");
        String noteId = createNote(user.id(), "Biology notes", "An overview of cells.");

        MvcResult result = mockMvc.perform(post(GENERATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FlashcardGenerateNoteRequest(noteId)))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andReturn();

        String deckId = objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("deckId")
            .asString();

        assertEquals(0, deckColumn(deckId, "review_count", Integer.class));
        assertEquals(0, deckColumn(deckId, "interval_days", Integer.class));
        assertEquals(new BigDecimal("2.50"), deckColumn(deckId, "ease_factor", BigDecimal.class));
        assertNull(deckColumn(deckId, "next_review_date", LocalDate.class));
        assertNull(deckColumn(deckId, "last_reviewed_at", LocalDateTime.class));
        assertNull(deckColumn(deckId, "last_rating", String.class));
    }

    @Test
    void registeredUsersStartWithNoLifetimeFlashcardsReviewed() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        assertEquals(0L, totalFlashcardsReviewed(user.id()));
    }

    @Test
    void deckEaseFactorCannotBeStoredBelowTheMinimum() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeckWithCards(user.id(), "deckease02", 1);

        assertThrows(
            DataIntegrityViolationException.class,
            () -> jdbcTemplate.update("UPDATE flashcard_deck SET ease_factor = 1.29 WHERE id = ?", deckId)
        );
    }

    @Test
    void deletingADeckDeletesItsReviewHistoryAndKeepsTheLifetimeCount() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long deckId = createDeckWithCards(user.id(), "deckgone01", 3);

        mockMvc.perform(post(REVIEW_ENDPOINT, "deckgone01")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"GOOD\"}")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        assertEquals(1, reviewCount(deckId));
        assertEquals(3L, totalFlashcardsReviewed(user.id()));

        mockMvc.perform(delete(DECK_ENDPOINT, "deckgone01")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        assertEquals(0, reviewCount(deckId));
        assertEquals(3L, totalFlashcardsReviewed(user.id()));
    }

    @Test
    void deletingCardsKeepsTheLifetimeCount() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeckWithCards(user.id(), "deckcard01", 2);

        mockMvc.perform(post(REVIEW_ENDPOINT, "deckcard01")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"GOOD\"}")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/flashcards/{deckId}/cards/{cardId}", "deckcard01", "card01c0")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        assertEquals(2L, totalFlashcardsReviewed(user.id()));
    }

    private <T> T deckColumn(String deckPublicId, String column, Class<T> type) {
        return jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM flashcard_deck WHERE public_id = ?",
            type,
            deckPublicId
        );
    }

    private int reviewCount(Long deckId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flashcard_deck_review WHERE deck_id = ?",
            Integer.class,
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

    private TestUser register(String fullName, String email) throws Exception {
        String accessToken = registerAndAuthenticate(fullName, email, VALID_PASSWORD);
        Long userId = Long.valueOf(jwtDecoder.decode(accessToken).getSubject());

        return new TestUser(userId, accessToken);
    }

    private String createNote(Long userId, String title, String overview) {
        String publicId = NanoIdUtils.randomNanoId(
            NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
            NanoIdUtils.DEFAULT_ALPHABET,
            10
        );

        jdbcTemplate.update(
            """
            INSERT INTO note (user_id, public_id, title, overview)
            VALUES (?, ?, ?, ?)
            """,
            userId,
            publicId,
            title,
            overview
        );

        return publicId;
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

    private String validFlashcardJson() {
        return """
            {
              "flashcards": [
                {
                  "title": "Mitochondria",
                  "answer": "The organelle that releases energy for the cell."
                }
              ]
            }
            """;
    }

    private record TestUser(
        Long id,
        String accessToken
    ) {}
}

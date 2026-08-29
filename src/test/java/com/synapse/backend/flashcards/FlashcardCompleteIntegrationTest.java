package com.synapse.backend.flashcards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

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
class FlashcardCompleteIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String COMPLETE_ENDPOINT = "/api/flashcards/{deckId}/complete";
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
    void completeDeckReturnsNoContentAndRecordsActivityForCurrentUtcDay() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeck(user.id(), "deckdone01", "Systems deck");

        mockMvc.perform(post(COMPLETE_ENDPOINT, "deckdone01")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        assertEquals(List.of(LocalDate.now(ZoneOffset.UTC)), activityDates(user.id()));
    }

    @Test
    void completeDeckTwiceOnSameDayRecordsOneActivityDay() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeck(user.id(), "deckdone02", "Systems deck");

        mockMvc.perform(post(COMPLETE_ENDPOINT, "deckdone02")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        mockMvc.perform(post(COMPLETE_ENDPOINT, "deckdone02")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        assertEquals(List.of(LocalDate.now(ZoneOffset.UTC)), activityDates(user.id()));
    }

    @Test
    void completeDeckReturnsNotFoundWhenDeckBelongsToAnotherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        createDeck(otherUser.id(), "deckdone03", "Private deck");

        mockMvc.perform(post(COMPLETE_ENDPOINT, "deckdone03")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Deck not found: deckdone03"));

        assertEquals(List.of(), activityDates(currentUser.id()));
        assertEquals(List.of(), activityDates(otherUser.id()));
    }

    @Test
    void completeDeckReturnsNotFoundWhenDeckDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(post(COMPLETE_ENDPOINT, "missing001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Deck not found: missing001"));

        assertEquals(List.of(), activityDates(user.id()));
    }

    @Test
    void completeDeckReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeck(user.id(), "deckdone04", "Systems deck");

        mockMvc.perform(post(COMPLETE_ENDPOINT, "deckdone04"))
            .andExpect(status().isUnauthorized());

        assertEquals(List.of(), activityDates(user.id()));
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

    private void createDeck(Long userId, String publicId, String title) {
        jdbcTemplate.update(
            """
            INSERT INTO flashcard_deck (user_id, title, source_type, public_id)
            VALUES (?, ?, 'NOTE', ?)
            """,
            userId,
            title,
            publicId
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

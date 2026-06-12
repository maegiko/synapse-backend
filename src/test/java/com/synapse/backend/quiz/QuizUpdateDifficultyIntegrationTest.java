package com.synapse.backend.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class QuizUpdateDifficultyIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String DIFFICULTY_ENDPOINT = "/api/quiz/{quizId}/difficulty";
    private static final String QUIZ_ENDPOINT = "/api/quiz/{quizId}";
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
    void resetDatabaseAndMocks() {
        jdbcTemplate.execute("DELETE FROM app_user");
        reset(llmClient);
    }

    @AfterEach
    void doesNotCallLlm() {
        verifyNoInteractions(llmClient);
    }

    @Test
    void updateDifficultyPersistsValueUpdatesTimestampAndReturnsItFromQuiz() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 2, 1, 9, 0);
        Long quizId = createQuiz(
            user.id(),
            "quizdiff01",
            "Systems quiz",
            "Checks systems thinking.",
            originalUpdatedAt
        );

        mockMvc.perform(put(DIFFICULTY_ENDPOINT, "quizdiff01")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("difficulty", 4))))
            .andExpect(status().isNoContent());

        assertEquals(4, quizDifficulty(quizId));
        assertTrue(quizUpdatedAt(quizId).isAfter(originalUpdatedAt));

        mockMvc.perform(get(QUIZ_ENDPOINT, "quizdiff01")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.difficulty").value(4));
    }

    @Test
    void updateDifficultyAcceptsMinimumAndMaximumValues() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(
            user.id(),
            "quizdiff02",
            "Boundary quiz",
            "Checks difficulty boundaries.",
            LocalDateTime.of(2026, 2, 2, 9, 0)
        );

        mockMvc.perform(put(DIFFICULTY_ENDPOINT, "quizdiff02")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("difficulty", 1))))
            .andExpect(status().isNoContent());

        assertEquals(1, quizDifficulty(quizId));

        mockMvc.perform(put(DIFFICULTY_ENDPOINT, "quizdiff02")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("difficulty", 5))))
            .andExpect(status().isNoContent());

        assertEquals(5, quizDifficulty(quizId));
    }

    @Test
    void updateDifficultyReturnsBadRequestWhenValueIsBelowMinimum() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(
            user.id(),
            "quizdiff03",
            "Invalid quiz",
            "Checks validation.",
            LocalDateTime.of(2026, 2, 3, 9, 0)
        );

        mockMvc.perform(put(DIFFICULTY_ENDPOINT, "quizdiff03")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("difficulty", 0))))
            .andExpect(status().isBadRequest());

        assertEquals(null, quizDifficulty(quizId));
    }

    @Test
    void updateDifficultyReturnsBadRequestWhenValueIsAboveMaximum() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(
            user.id(),
            "quizdiff04",
            "Invalid quiz",
            "Checks validation.",
            LocalDateTime.of(2026, 2, 4, 9, 0)
        );

        mockMvc.perform(put(DIFFICULTY_ENDPOINT, "quizdiff04")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("difficulty", 6))))
            .andExpect(status().isBadRequest());

        assertEquals(null, quizDifficulty(quizId));
    }

    @Test
    void updateDifficultyReturnsBadRequestWhenDifficultyIsMissing() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(
            user.id(),
            "quizdiff05",
            "Invalid quiz",
            "Checks validation.",
            LocalDateTime.of(2026, 2, 5, 9, 0)
        );

        mockMvc.perform(put(DIFFICULTY_ENDPOINT, "quizdiff05")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());

        assertEquals(null, quizDifficulty(quizId));
    }

    @Test
    void updateDifficultyReturnsBadRequestWhenDifficultyIsNull() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(
            user.id(),
            "quizdiff06",
            "Invalid quiz",
            "Checks validation.",
            LocalDateTime.of(2026, 2, 6, 9, 0)
        );

        mockMvc.perform(put(DIFFICULTY_ENDPOINT, "quizdiff06")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "difficulty": null
                    }
                    """))
            .andExpect(status().isBadRequest());

        assertEquals(null, quizDifficulty(quizId));
    }

    @Test
    void updateDifficultyDoesNotUpdateQuizOwnedByAnotherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        Long otherQuizId = createQuiz(
            otherUser.id(),
            "quizdiff07",
            "Private quiz",
            "This quiz belongs to another user.",
            LocalDateTime.of(2026, 2, 7, 9, 0)
        );

        mockMvc.perform(put(DIFFICULTY_ENDPOINT, "quizdiff07")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("difficulty", 3))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: quizdiff07"));

        assertEquals(null, quizDifficulty(otherQuizId));
    }

    @Test
    void updateDifficultyReturnsNotFoundWhenQuizDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(put(DIFFICULTY_ENDPOINT, "missing001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("difficulty", 3))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: missing001"));
    }

    @Test
    void updateDifficultyReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(put(DIFFICULTY_ENDPOINT, "quizdiff08")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("difficulty", 3))))
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

    private Long createQuiz(
        Long userId,
        String publicId,
        String title,
        String description,
        LocalDateTime updatedAt
    ) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO quiz (user_id, public_id, title, description, source_type, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'MANUAL', ?::timestamp, ?::timestamp)
            RETURNING id
            """,
            Long.class,
            userId,
            publicId,
            title,
            description,
            Timestamp.valueOf(updatedAt),
            Timestamp.valueOf(updatedAt)
        );
    }

    private Integer quizDifficulty(Long quizId) {
        return jdbcTemplate.queryForObject(
            "SELECT difficulty FROM quiz WHERE id = ?",
            Integer.class,
            quizId
        );
    }

    private LocalDateTime quizUpdatedAt(Long quizId) {
        return jdbcTemplate.queryForObject(
            "SELECT updated_at FROM quiz WHERE id = ?",
            LocalDateTime.class,
            quizId
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

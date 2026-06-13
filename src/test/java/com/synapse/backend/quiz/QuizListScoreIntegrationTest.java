package com.synapse.backend.quiz;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;

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
class QuizListScoreIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String SCORE_LIST_ENDPOINT = "/api/quiz/{quizId}/score/list";
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
    void getAllQuizScoresReturnsNewestAttemptsFirstWithStoredQuestionTotals() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizscores", "Systems quiz", "Checks systems thinking.");
        createScore(
            quizId,
            user.id(),
            "scoreold01",
            4,
            5,
            LocalDateTime.of(2026, 2, 1, 9, 0)
        );
        createScore(
            quizId,
            user.id(),
            "scorenew01",
            7,
            8,
            LocalDateTime.of(2026, 2, 2, 9, 0)
        );

        mockMvc.perform(get(SCORE_LIST_ENDPOINT, "quizscores")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scores", hasSize(2)))
            .andExpect(jsonPath("$.scores[0].publicId").value("scorenew01"))
            .andExpect(jsonPath("$.scores[0].quizId").value("quizscores"))
            .andExpect(jsonPath("$.scores[0].score").value(7))
            .andExpect(jsonPath("$.scores[0].totalQuestions").value(8))
            .andExpect(jsonPath("$.scores[0].createdAt").isNotEmpty())
            .andExpect(jsonPath("$.scores[1].publicId").value("scoreold01"))
            .andExpect(jsonPath("$.scores[1].quizId").value("quizscores"))
            .andExpect(jsonPath("$.scores[1].score").value(4))
            .andExpect(jsonPath("$.scores[1].totalQuestions").value(5))
            .andExpect(jsonPath("$.scores[1].createdAt").isNotEmpty());
    }

    @Test
    void getAllQuizScoresDoesNotReturnAttemptsFromAnotherQuiz() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long requestedQuizId = createQuiz(user.id(), "quizscorea", "Requested quiz", "Return this history.");
        Long otherQuizId = createQuiz(user.id(), "quizscoreb", "Other quiz", "Do not return this history.");
        createScore(
            requestedQuizId,
            user.id(),
            "scorequiz1",
            2,
            3,
            LocalDateTime.of(2026, 2, 1, 9, 0)
        );
        createScore(
            otherQuizId,
            user.id(),
            "scorequiz2",
            3,
            4,
            LocalDateTime.of(2026, 2, 2, 9, 0)
        );

        mockMvc.perform(get(SCORE_LIST_ENDPOINT, "quizscorea")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scores", hasSize(1)))
            .andExpect(jsonPath("$.scores[0].publicId").value("scorequiz1"))
            .andExpect(jsonPath("$.scores[0].quizId").value("quizscorea"));
    }

    @Test
    void getAllQuizScoresReturnsEmptyListWhenQuizHasNoAttempts() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuiz(user.id(), "quizempty1", "Empty history", "No attempts yet.");

        mockMvc.perform(get(SCORE_LIST_ENDPOINT, "quizempty1")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scores", hasSize(0)));
    }

    @Test
    void getAllQuizScoresDoesNotReturnHistoryForQuizOwnedByAnotherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        Long quizId = createQuiz(otherUser.id(), "quizpriv01", "Private quiz", "This quiz belongs to another user.");
        createScore(
            quizId,
            otherUser.id(),
            "scorepriv1",
            2,
            3,
            LocalDateTime.of(2026, 2, 1, 9, 0)
        );

        mockMvc.perform(get(SCORE_LIST_ENDPOINT, "quizpriv01")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: quizpriv01"));
    }

    @Test
    void getAllQuizScoresReturnsNotFoundWhenQuizDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(SCORE_LIST_ENDPOINT, "missing001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: missing001"));
    }

    @Test
    void getAllQuizScoresReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get(SCORE_LIST_ENDPOINT, "quizscore1"))
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

    private Long createQuiz(Long userId, String publicId, String title, String description) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO quiz (user_id, public_id, title, description, source_type)
            VALUES (?, ?, ?, ?, 'MANUAL')
            RETURNING id
            """,
            Long.class,
            userId,
            publicId,
            title,
            description
        );
    }

    private void createScore(
        Long quizId,
        Long userId,
        String publicId,
        int score,
        int totalQuestions,
        LocalDateTime createdAt
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO quiz_score (
                quiz_id,
                user_id,
                public_id,
                score,
                total_questions,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?::timestamp)
            """,
            quizId,
            userId,
            publicId,
            score,
            totalQuestions,
            Timestamp.valueOf(createdAt)
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

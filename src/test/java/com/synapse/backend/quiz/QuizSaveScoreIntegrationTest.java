package com.synapse.backend.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class QuizSaveScoreIntegrationTest extends PostgresIntegrationTest {

    private static final String SCORE_ENDPOINT = "/api/quiz/{quizId}/score";
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
    void saveScorePersistsAttemptAndReturnsScoreDetails() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizscore1", "Systems quiz", "Checks systems thinking.");
        createQuestions(quizId, 3);

        MvcResult result = mockMvc.perform(post(SCORE_ENDPOINT, "quizscore1")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("score", 2))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.publicId").isNotEmpty())
            .andExpect(jsonPath("$.quizId").value("quizscore1"))
            .andExpect(jsonPath("$.score").value(2))
            .andExpect(jsonPath("$.totalQuestions").value(3))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andReturn();

        String scorePublicId = objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("publicId")
            .asString();

        assertEquals(10, scorePublicId.length());

        ScoreRow savedScore = findScore(scorePublicId);
        assertEquals(quizId, savedScore.quizId());
        assertEquals(user.id(), savedScore.userId());
        assertEquals(2, savedScore.score());
        assertEquals(3, savedScore.totalQuestions());
    }

    @Test
    void saveScoreAcceptsZeroAndFullScores() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizscore2", "Boundary quiz", "Checks score boundaries.");
        createQuestions(quizId, 2);

        mockMvc.perform(post(SCORE_ENDPOINT, "quizscore2")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("score", 0))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.score").value(0))
            .andExpect(jsonPath("$.totalQuestions").value(2));

        mockMvc.perform(post(SCORE_ENDPOINT, "quizscore2")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("score", 2))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.score").value(2))
            .andExpect(jsonPath("$.totalQuestions").value(2));

        assertEquals(2L, countScores(quizId));
    }

    @Test
    void saveScoreReturnsBadRequestWhenScoreIsNegative() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizscore3", "Invalid quiz", "Checks validation.");
        createQuestions(quizId, 2);

        mockMvc.perform(post(SCORE_ENDPOINT, "quizscore3")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("score", -1))))
            .andExpect(status().isBadRequest());

        assertEquals(0L, countScores(quizId));
    }

    @Test
    void saveScoreReturnsBadRequestWhenScoreIsMissing() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizscore4", "Invalid quiz", "Checks validation.");
        createQuestions(quizId, 2);

        mockMvc.perform(post(SCORE_ENDPOINT, "quizscore4")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());

        assertEquals(0L, countScores(quizId));
    }

    @Test
    void saveScoreReturnsBadRequestWhenScoreIsNull() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizscore5", "Invalid quiz", "Checks validation.");
        createQuestions(quizId, 2);

        mockMvc.perform(post(SCORE_ENDPOINT, "quizscore5")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "score": null
                    }
                    """))
            .andExpect(status().isBadRequest());

        assertEquals(0L, countScores(quizId));
    }

    @Test
    void saveScoreReturnsBadRequestWhenScoreExceedsQuestionCount() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizscore6", "Invalid quiz", "Checks validation.");
        createQuestions(quizId, 2);

        mockMvc.perform(post(SCORE_ENDPOINT, "quizscore6")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("score", 3))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Score cannot be greater than number of questions."));

        assertEquals(0L, countScores(quizId));
    }

    @Test
    void saveScoreDoesNotAddAttemptToQuizOwnedByAnotherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        Long quizId = createQuiz(otherUser.id(), "quizscore7", "Private quiz", "This quiz belongs to another user.");
        createQuestions(quizId, 2);

        mockMvc.perform(post(SCORE_ENDPOINT, "quizscore7")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("score", 1))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: quizscore7"));

        assertEquals(0L, countScores(quizId));
    }

    @Test
    void saveScoreReturnsNotFoundWhenQuizDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(post(SCORE_ENDPOINT, "missing001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("score", 1))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: missing001"));
    }

    @Test
    void saveScoreReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(post(SCORE_ENDPOINT, "quizscore8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("score", 1))))
            .andExpect(status().isUnauthorized());
    }

    private TestUser register(String fullName, String email) throws Exception {
        String accessToken = registerAndAuthenticate(fullName, email, VALID_PASSWORD);
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

    private void createQuestions(Long quizId, int numberOfQuestions) {
        for (int position = 0; position < numberOfQuestions; position++) {
            jdbcTemplate.update(
                """
                INSERT INTO quiz_question (
                    quiz_id,
                    public_id,
                    question_text,
                    question_type,
                    position
                )
                VALUES (?, ?, ?, 'BOOLEAN', ?)
                """,
                quizId,
                "question" + position,
                "Question " + position,
                position
            );
        }
    }

    private ScoreRow findScore(String publicId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT quiz_id, user_id, score, total_questions
            FROM quiz_score
            WHERE public_id = ?
            """,
            (rs, rowNum) -> new ScoreRow(
                rs.getLong("quiz_id"),
                rs.getLong("user_id"),
                rs.getInt("score"),
                rs.getInt("total_questions")
            ),
            publicId
        );
    }

    private Long countScores(Long quizId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM quiz_score WHERE quiz_id = ?",
            Long.class,
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

    private record ScoreRow(
        Long quizId,
        Long userId,
        int score,
        int totalQuestions
    ) {}
}

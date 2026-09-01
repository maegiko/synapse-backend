package com.synapse.backend.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.support.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
class QuizDeleteQuestionIntegrationTest extends PostgresIntegrationTest {

    private static final String DELETE_QUESTION_ENDPOINT = "/api/quiz/{quizId}/questions/{questionId}";
    private static final String VALID_PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

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
    void deleteQuestionDeletesCurrentUsersQuestionAndAnswersAndUpdatesQuizTimestamp() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 2, 1, 9, 0);
        Long quizId = createQuiz(user.id(), "quizdq0001", "Quiz", "Question delete test.", originalUpdatedAt);
        Long questionId = createQuestion(quizId, "questdq001", "Question to delete", "BOOLEAN", 0);
        Long remainingQuestionId = createQuestion(quizId, "questdq002", "Question to keep", "BOOLEAN", 1);
        createAnswer(questionId, "answrdq001", "True", true, 0);
        createAnswer(questionId, "answrdq002", "False", false, 1);
        createAnswer(remainingQuestionId, "answrdq003", "True", true, 0);

        mockMvc.perform(delete(DELETE_QUESTION_ENDPOINT, "quizdq0001", "questdq001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        assertEquals(0L, countQuestions(quizId, "questdq001"));
        assertEquals(0L, countAnswers(questionId));
        assertEquals(1L, countQuestions(quizId, "questdq002"));
        assertEquals(1L, countAnswers(remainingQuestionId));
        assertTrue(quizUpdatedAt(quizId).isAfter(originalUpdatedAt));
    }

    @Test
    void deleteQuestionDoesNotDeleteQuestionFromQuizOwnedByAnotherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        Long otherQuizId = createQuiz(
            otherUser.id(),
            "quizdq0002",
            "Private quiz",
            "This quiz belongs to another user.",
            LocalDateTime.of(2026, 2, 2, 9, 0)
        );
        Long questionId = createQuestion(otherQuizId, "questdq003", "Private question", "BOOLEAN", 0);
        createAnswer(questionId, "answrdq004", "True", true, 0);

        mockMvc.perform(delete(DELETE_QUESTION_ENDPOINT, "quizdq0002", "questdq003")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: quizdq0002"));

        assertEquals(1L, countQuestions(otherQuizId, "questdq003"));
        assertEquals(1L, countAnswers(questionId));
    }

    @Test
    void deleteQuestionReturnsNotFoundWhenQuizDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(delete(DELETE_QUESTION_ENDPOINT, "missing001", "questdq004")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: missing001"));
    }

    @Test
    void deleteQuestionReturnsNotFoundWhenQuestionDoesNotExistInQuiz() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(
            user.id(),
            "quizdq0003",
            "Quiz",
            "Missing question test.",
            LocalDateTime.of(2026, 2, 3, 9, 0)
        );

        mockMvc.perform(delete(DELETE_QUESTION_ENDPOINT, "quizdq0003", "missing002")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Question not found: missing002"));

        assertEquals(0L, countQuestions(quizId));
    }

    @Test
    void deleteQuestionDoesNotDeleteQuestionFromDifferentQuizOwnedBySameUser() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long firstQuizId = createQuiz(
            user.id(),
            "quizdq0004",
            "First quiz",
            "Question should stay elsewhere.",
            LocalDateTime.of(2026, 2, 4, 9, 0)
        );
        Long secondQuizId = createQuiz(
            user.id(),
            "quizdq0005",
            "Second quiz",
            "Question should not be found here.",
            LocalDateTime.of(2026, 2, 4, 10, 0)
        );
        Long questionId = createQuestion(firstQuizId, "questdq005", "Question in first quiz", "BOOLEAN", 0);
        createAnswer(questionId, "answrdq005", "True", true, 0);

        mockMvc.perform(delete(DELETE_QUESTION_ENDPOINT, "quizdq0005", "questdq005")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Question not found: questdq005"));

        assertEquals(1L, countQuestions(firstQuizId, "questdq005"));
        assertEquals(0L, countQuestions(secondQuizId));
        assertEquals(1L, countAnswers(questionId));
    }

    @Test
    void deleteQuestionReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(delete(DELETE_QUESTION_ENDPOINT, "quizdq0006", "questdq006"))
            .andExpect(status().isUnauthorized());
    }

    private TestUser register(String fullName, String email) throws Exception {
        String accessToken = registerAndAuthenticate(fullName, email, VALID_PASSWORD);
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

    private Long createQuestion(
        Long quizId,
        String publicId,
        String questionText,
        String questionType,
        int position
    ) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO quiz_question (quiz_id, public_id, question_text, question_type, position)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            quizId,
            publicId,
            questionText,
            questionType,
            position
        );
    }

    private void createAnswer(
        Long questionId,
        String publicId,
        String answerText,
        boolean correct,
        int position
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO quiz_answer (question_id, public_id, answer_text, is_correct, position)
            VALUES (?, ?, ?, ?, ?)
            """,
            questionId,
            publicId,
            answerText,
            correct,
            position
        );
    }

    private Long countQuestions(Long quizId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM quiz_question WHERE quiz_id = ?",
            Long.class,
            quizId
        );
    }

    private Long countQuestions(Long quizId, String publicId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM quiz_question WHERE quiz_id = ? AND public_id = ?",
            Long.class,
            quizId,
            publicId
        );
    }

    private Long countAnswers(Long questionId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM quiz_answer WHERE question_id = ?",
            Long.class,
            questionId
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

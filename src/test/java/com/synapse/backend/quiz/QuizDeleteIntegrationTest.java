package com.synapse.backend.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class QuizDeleteIntegrationTest extends PostgresIntegrationTest {

    private static final String QUIZ_ENDPOINT = "/api/quiz/{quizId}";
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
    void doesNotGenerateQuiz() {
        verifyNoInteractions(llmClient);
    }

    @Test
    void deleteQuizDeletesCurrentUsersQuizQuestionsAndAnswers() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizdel001", "Quiz to delete", "Delete me.");
        Long questionId = createQuestion(quizId, "questdel01", "Question to delete", "BOOLEAN", 0);
        createAnswer(questionId, "answdel001", "True", true, 0);
        createAnswer(questionId, "answdel002", "False", false, 1);

        mockMvc.perform(delete(QUIZ_ENDPOINT, "quizdel001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        assertEquals(0L, countRows("quiz"));
        assertEquals(0L, countRows("quiz_question"));
        assertEquals(0L, countRows("quiz_answer"));
    }

    @Test
    void deleteQuizDoesNotDeleteQuizOwnedByAnotherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        Long quizId = createQuiz(otherUser.id(), "quizpriv01", "Private quiz", "Not yours.");
        Long questionId = createQuestion(quizId, "questpriv1", "Private question", "BOOLEAN", 0);
        createAnswer(questionId, "answpriv01", "True", true, 0);

        mockMvc.perform(delete(QUIZ_ENDPOINT, "quizpriv01")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: quizpriv01"));

        assertEquals(1L, countRows("quiz"));
        assertEquals(1L, countRows("quiz_question"));
        assertEquals(1L, countRows("quiz_answer"));
    }

    @Test
    void deleteQuizReturnsNotFoundWhenQuizDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(delete(QUIZ_ENDPOINT, "missing001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: missing001"));
    }

    @Test
    void deleteQuizReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(delete(QUIZ_ENDPOINT, "quizdel001"))
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

    private Long countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record TestUser(
        Long id,
        String accessToken
    ) {}
}

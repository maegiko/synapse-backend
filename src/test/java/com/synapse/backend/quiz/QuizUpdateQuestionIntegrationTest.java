package com.synapse.backend.quiz;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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
class QuizUpdateQuestionIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String QUESTION_ENDPOINT = "/api/quiz/{quizId}/questions/{questionId}";
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
    void updateQuestionUpdatesOnlyTextKeepsAnswersAndAdvancesQuizTimestamp() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 2, 1, 9, 0);
        Long quizId = createQuiz(user.id(), "quizqup001", originalUpdatedAt);
        Long questionId = createQuestion(quizId, "question01", "Old text", "BOOLEAN", 0);
        createAnswer(questionId, "answer0001", "True", true, 0);
        createAnswer(questionId, "answer0002", "False", false, 1);

        Map<String, Object> body = new HashMap<>();
        body.put("question", "A balancing loop always reduces change.");

        mockMvc.perform(patch(QUESTION_ENDPOINT, "quizqup001", "question01")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("question01"))
            .andExpect(jsonPath("$.question").value("A balancing loop always reduces change."))
            .andExpect(jsonPath("$.questionType").value("BOOLEAN"))
            .andExpect(jsonPath("$.answers", hasSize(2)))
            .andExpect(jsonPath("$.answers[0].id").value("answer0001"))
            .andExpect(jsonPath("$.answers[0].answer").value("True"))
            .andExpect(jsonPath("$.answers[0].isCorrect").value(true));

        assertEquals("A balancing loop always reduces change.", questionColumn(quizId, "question01", "question_text"));
        assertEquals(2L, countAnswers(questionId));
        assertTrue(quizUpdatedAt(quizId).isAfter(originalUpdatedAt));
    }

    @Test
    void updateQuestionReplacesTheWholeAnswerSetWhenAnswersAreSupplied() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizqup002", LocalDateTime.of(2026, 2, 2, 9, 0));
        Long questionId = createQuestion(quizId, "question02", "The question", "BOOLEAN", 0);
        createAnswer(questionId, "answer0010", "True", true, 0);
        createAnswer(questionId, "answer0011", "False", false, 1);

        mockMvc.perform(patch(QUESTION_ENDPOINT, "quizqup002", "question02")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "questionType", "MULTIPLE_CHOICE",
                    "answers", List.of(
                        Map.of("answer", "A loop that amplifies change", "isCorrect", true),
                        Map.of("answer", "A loop that balances change", "isCorrect", false),
                        Map.of("answer", "A fixed delay", "isCorrect", false),
                        Map.of("answer", "A single stock", "isCorrect", false)
                    )
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questionType").value("MULTIPLE_CHOICE"))
            .andExpect(jsonPath("$.answers", hasSize(4)))
            .andExpect(jsonPath("$.answers[0].answer").value("A loop that amplifies change"))
            .andExpect(jsonPath("$.answers[0].isCorrect").value(true))
            .andExpect(jsonPath("$.answers[3].answer").value("A single stock"));

        assertEquals(4L, countAnswers(questionId));
        assertEquals(0L, countAnswersWithText(questionId, "True"));
        assertEquals("MULTIPLE_CHOICE", questionColumn(quizId, "question02", "question_type"));

        mockMvc.perform(get(QUIZ_ENDPOINT, "quizqup002")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questions[0].answers", hasSize(4)))
            .andExpect(jsonPath("$.questions[0].answers[0].text").value("A loop that amplifies change"));
    }

    @Test
    void updateQuestionUpdatesTextTypeAndAnswersTogether() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizqup003", LocalDateTime.of(2026, 2, 3, 9, 0));
        Long questionId = createQuestion(quizId, "question03", "Old text", "MULTIPLE_CHOICE", 0);
        createAnswer(questionId, "answer0020", "A", true, 0);
        createAnswer(questionId, "answer0021", "B", false, 1);
        createAnswer(questionId, "answer0022", "C", false, 2);
        createAnswer(questionId, "answer0023", "D", false, 3);

        mockMvc.perform(patch(QUESTION_ENDPOINT, "quizqup003", "question03")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "question", "A sequence diagram shows interactions over time.",
                    "questionType", "BOOLEAN",
                    "answers", List.of(
                        Map.of("answer", "True", "isCorrect", true),
                        Map.of("answer", "False", "isCorrect", false)
                    )
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.question").value("A sequence diagram shows interactions over time."))
            .andExpect(jsonPath("$.questionType").value("BOOLEAN"))
            .andExpect(jsonPath("$.answers", hasSize(2)));

        assertEquals(2L, countAnswers(questionId));
    }

    @Test
    void updateQuestionRejectsAnswerCountThatDoesNotMatchTheResultingType() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizqup004", LocalDateTime.of(2026, 2, 4, 9, 0));
        Long questionId = createQuestion(quizId, "question04", "Old text", "BOOLEAN", 0);
        createAnswer(questionId, "answer0030", "True", true, 0);
        createAnswer(questionId, "answer0031", "False", false, 1);

        mockMvc.perform(patch(QUESTION_ENDPOINT, "quizqup004", "question04")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "questionType", "MULTIPLE_CHOICE",
                    "answers", List.of(
                        Map.of("answer", "One", "isCorrect", true),
                        Map.of("answer", "Two", "isCorrect", false),
                        Map.of("answer", "Three", "isCorrect", false)
                    )
                ))))
            .andExpect(status().isBadRequest());

        assertEquals(2L, countAnswers(questionId));
        assertEquals("BOOLEAN", questionColumn(quizId, "question04", "question_type"));
    }

    @Test
    void updateQuestionRejectsChangingTypeWithoutMatchingAnswers() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizqup005", LocalDateTime.of(2026, 2, 5, 9, 0));
        Long questionId = createQuestion(quizId, "question05", "Old text", "BOOLEAN", 0);
        createAnswer(questionId, "answer0040", "True", true, 0);
        createAnswer(questionId, "answer0041", "False", false, 1);

        Map<String, Object> body = new HashMap<>();
        body.put("questionType", "MULTIPLE_CHOICE");

        mockMvc.perform(patch(QUESTION_ENDPOINT, "quizqup005", "question05")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());

        assertEquals("BOOLEAN", questionColumn(quizId, "question05", "question_type"));
    }

    @Test
    void updateQuestionRejectsAnswerSetWithoutExactlyOneCorrectAnswer() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizqup006", LocalDateTime.of(2026, 2, 6, 9, 0));
        Long questionId = createQuestion(quizId, "question06", "Old text", "BOOLEAN", 0);
        createAnswer(questionId, "answer0050", "True", true, 0);
        createAnswer(questionId, "answer0051", "False", false, 1);

        mockMvc.perform(patch(QUESTION_ENDPOINT, "quizqup006", "question06")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "answers", List.of(
                        Map.of("answer", "True", "isCorrect", true),
                        Map.of("answer", "False", "isCorrect", true)
                    )
                ))))
            .andExpect(status().isBadRequest());

        assertEquals(1L, countCorrectAnswers(questionId));
    }

    @Test
    void updateQuestionValidatesResultingQuestionAndDoesNotMutateOnFailure() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizqup007", LocalDateTime.of(2026, 2, 7, 9, 0));
        Long questionId = createQuestion(quizId, "question07", "Old text", "MULTIPLE_CHOICE", 0);
        createAnswer(questionId, "answer0060", "A", true, 0);
        createAnswer(questionId, "answer0061", "B", true, 1);
        createAnswer(questionId, "answer0062", "C", false, 2);
        createAnswer(questionId, "answer0063", "D", false, 3);

        Map<String, Object> body = new HashMap<>();
        body.put("question", "New text");

        mockMvc.perform(patch(QUESTION_ENDPOINT, "quizqup007", "question07")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());

        assertEquals("Old text", questionColumn(quizId, "question07", "question_text"));
    }

    @Test
    void updateQuestionRejectsAnEmptyRequest() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizqup008", LocalDateTime.of(2026, 2, 8, 9, 0));
        createQuestion(quizId, "question08", "The text", "BOOLEAN", 0);

        mockMvc.perform(patch(QUESTION_ENDPOINT, "quizqup008", "question08")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                .value("At least one of question, questionType, or answers must be supplied."));

        assertEquals("The text", questionColumn(quizId, "question08", "question_text"));
    }

    @Test
    void updateQuestionRejectsBlankQuestionText() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizqup009", LocalDateTime.of(2026, 2, 9, 9, 0));
        createQuestion(quizId, "question09", "The text", "BOOLEAN", 0);

        Map<String, Object> body = new HashMap<>();
        body.put("question", "   ");

        mockMvc.perform(patch(QUESTION_ENDPOINT, "quizqup009", "question09")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("question: must not be blank"));

        assertEquals("The text", questionColumn(quizId, "question09", "question_text"));
    }

    @Test
    void updateQuestionRejectsBlankAnswerText() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizqup010", LocalDateTime.of(2026, 2, 10, 9, 0));
        Long questionId = createQuestion(quizId, "question10", "The text", "BOOLEAN", 0);
        createAnswer(questionId, "answer0070", "True", true, 0);
        createAnswer(questionId, "answer0071", "False", false, 1);

        mockMvc.perform(patch(QUESTION_ENDPOINT, "quizqup010", "question10")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "answers": [
                            { "answer": " ", "isCorrect": true },
                            { "answer": "False", "isCorrect": false }
                        ]
                    }
                    """))
            .andExpect(status().isBadRequest());

        assertEquals(1L, countAnswersWithText(questionId, "True"));
    }

    @Test
    void updateQuestionReturnsNotFoundWhenQuizDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(patch(QUESTION_ENDPOINT, "missing001", "question01")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("question", "New text"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: missing001"));
    }

    @Test
    void updateQuestionReturnsNotFoundWhenQuestionDoesNotExistInQuiz() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuiz(user.id(), "quizqup011", LocalDateTime.of(2026, 2, 11, 9, 0));

        mockMvc.perform(patch(QUESTION_ENDPOINT, "quizqup011", "missing002")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("question", "New text"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Question not found: missing002"));
    }

    @Test
    void updateQuestionDoesNotUpdateQuestionInQuizOwnedByAnotherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 2, 12, 9, 0);
        Long otherQuizId = createQuiz(otherUser.id(), "quizqup012", originalUpdatedAt);
        Long questionId = createQuestion(otherQuizId, "question12", "Private text", "BOOLEAN", 0);
        createAnswer(questionId, "answer0080", "True", true, 0);
        createAnswer(questionId, "answer0081", "False", false, 1);

        mockMvc.perform(patch(QUESTION_ENDPOINT, "quizqup012", "question12")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("question", "Stolen text"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: quizqup012"));

        assertEquals("Private text", questionColumn(otherQuizId, "question12", "question_text"));
        assertEquals(originalUpdatedAt, quizUpdatedAt(otherQuizId));
    }

    @Test
    void updateQuestionReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(patch(QUESTION_ENDPOINT, "quizqup013", "question13")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("question", "New text"))))
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

    private Long createQuiz(Long userId, String publicId, LocalDateTime updatedAt) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO quiz (user_id, public_id, title, description, source_type, created_at, updated_at)
            VALUES (?, ?, 'A quiz', 'A description.', 'MANUAL', ?::timestamp, ?::timestamp)
            RETURNING id
            """,
            Long.class,
            userId,
            publicId,
            Timestamp.valueOf(updatedAt),
            Timestamp.valueOf(updatedAt)
        );
    }

    private Long createQuestion(Long quizId, String publicId, String questionText, String questionType, int position) {
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

    private void createAnswer(Long questionId, String publicId, String answerText, boolean correct, int position) {
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

    private String questionColumn(Long quizId, String questionPublicId, String column) {
        return jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM quiz_question WHERE quiz_id = ? AND public_id = ?",
            String.class,
            quizId,
            questionPublicId
        );
    }

    private Long countAnswers(Long questionId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM quiz_answer WHERE question_id = ?",
            Long.class,
            questionId
        );
    }

    private Long countCorrectAnswers(Long questionId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM quiz_answer WHERE question_id = ? AND is_correct = true",
            Long.class,
            questionId
        );
    }

    private Long countAnswersWithText(Long questionId, String answerText) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM quiz_answer WHERE question_id = ? AND answer_text = ?",
            Long.class,
            questionId,
            answerText
        );
    }

    private LocalDateTime quizUpdatedAt(Long quizId) {
        return jdbcTemplate.queryForObject("SELECT updated_at FROM quiz WHERE id = ?", LocalDateTime.class, quizId);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record TestUser(
        Long id,
        String accessToken
    ) {}
}

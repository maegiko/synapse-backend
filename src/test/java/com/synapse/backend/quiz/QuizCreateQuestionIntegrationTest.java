package com.synapse.backend.quiz;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class QuizCreateQuestionIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String CREATE_QUESTION_ENDPOINT = "/api/quiz/{quizId}/questions";
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
    void createQuestionAddsQuestionToCurrentUsersQuizAndReturnsCreatedQuestion() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 2, 1, 9, 0);
        Long quizId = createQuiz(
            user.id(),
            "quizadd001",
            "Systems quiz",
            "Checks systems thinking.",
            originalUpdatedAt
        );
        createQuestion(quizId, "question01", "Existing question", "BOOLEAN", 0);

        MvcResult result = mockMvc.perform(post(CREATE_QUESTION_ENDPOINT, "quizadd001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "question", "What is a reinforcing loop?",
                    "questionType", "MULTIPLE_CHOICE",
                    "answers", List.of(
                        Map.of("answer", "A loop that amplifies change", "isCorrect", true),
                        Map.of("answer", "A loop that balances change", "isCorrect", false),
                        Map.of("answer", "A fixed delay", "isCorrect", false),
                        Map.of("answer", "A single stock", "isCorrect", false)
                    )
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.question").value("What is a reinforcing loop?"))
            .andExpect(jsonPath("$.questionType").value("MULTIPLE_CHOICE"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.answers", hasSize(4)))
            .andExpect(jsonPath("$.answers[0].id").isNotEmpty())
            .andExpect(jsonPath("$.answers[0].answer").value("A loop that amplifies change"))
            .andExpect(jsonPath("$.answers[0].isCorrect").value(true))
            .andExpect(jsonPath("$.answers[1].answer").value("A loop that balances change"))
            .andExpect(jsonPath("$.answers[1].isCorrect").value(false))
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String newQuestionPublicId = response.get("id").asString();

        mockMvc.perform(get(QUIZ_ENDPOINT, "quizadd001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questions", hasSize(2)))
            .andExpect(jsonPath("$.questions[0].id").value("question01"))
            .andExpect(jsonPath("$.questions[1].id").value(newQuestionPublicId))
            .andExpect(jsonPath("$.questions[1].text").value("What is a reinforcing loop?"))
            .andExpect(jsonPath("$.questions[1].questionType").value("MULTIPLE_CHOICE"))
            .andExpect(jsonPath("$.questions[1].answers", hasSize(4)))
            .andExpect(jsonPath("$.questions[1].answers[0].text").value("A loop that amplifies change"))
            .andExpect(jsonPath("$.questions[1].answers[0].correct").value(true))
            .andExpect(jsonPath("$.questions[1].answers[1].text").value("A loop that balances change"))
            .andExpect(jsonPath("$.questions[1].answers[1].correct").value(false));

        assertEquals(1L, countQuestions(quizId, "What is a reinforcing loop?"));
        assertTrue(quizUpdatedAt(quizId).isAfter(originalUpdatedAt));
    }

    @Test
    void createQuestionCanAddBooleanQuestionWithTwoAnswers() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuiz(
            user.id(),
            "quizbool01",
            "Boolean quiz",
            "Checks boolean questions.",
            LocalDateTime.of(2026, 2, 2, 9, 0)
        );

        mockMvc.perform(post(CREATE_QUESTION_ENDPOINT, "quizbool01")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "question", "A balancing loop always reduces change.",
                    "questionType", "BOOLEAN",
                    "answers", List.of(
                        Map.of("answer", "True", "isCorrect", false),
                        Map.of("answer", "False", "isCorrect", true)
                    )
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.question").value("A balancing loop always reduces change."))
            .andExpect(jsonPath("$.questionType").value("BOOLEAN"))
            .andExpect(jsonPath("$.answers", hasSize(2)))
            .andExpect(jsonPath("$.answers[0].answer").value("True"))
            .andExpect(jsonPath("$.answers[0].isCorrect").value(false))
            .andExpect(jsonPath("$.answers[1].answer").value("False"))
            .andExpect(jsonPath("$.answers[1].isCorrect").value(true));
    }

    @Test
    void createQuestionDoesNotAddQuestionToQuizOwnedByAnotherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        Long otherQuizId = createQuiz(
            otherUser.id(),
            "quizhide01",
            "Private quiz",
            "This quiz belongs to another user.",
            LocalDateTime.of(2026, 2, 3, 9, 0)
        );

        mockMvc.perform(post(CREATE_QUESTION_ENDPOINT, "quizhide01")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBooleanQuestionJson()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: quizhide01"));

        assertEquals(0L, countQuestions(otherQuizId));
    }

    @Test
    void createQuestionReturnsNotFoundWhenQuizDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(post(CREATE_QUESTION_ENDPOINT, "missing001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBooleanQuestionJson()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: missing001"));
    }

    @Test
    void createQuestionReturnsBadRequestWhenQuestionTextIsBlank() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuiz(user.id(), "quizbad001", "Invalid quiz", "Checks validation.", LocalDateTime.now());

        mockMvc.perform(post(CREATE_QUESTION_ENDPOINT, "quizbad001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "question", " ",
                    "questionType", "BOOLEAN",
                    "answers", List.of(
                        Map.of("answer", "True", "isCorrect", true),
                        Map.of("answer", "False", "isCorrect", false)
                    )
                ))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createQuestionReturnsBadRequestWhenAnswerTextIsBlank() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuiz(user.id(), "quizbad002", "Invalid quiz", "Checks validation.", LocalDateTime.now());

        mockMvc.perform(post(CREATE_QUESTION_ENDPOINT, "quizbad002")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "question", "Is this valid?",
                    "questionType", "BOOLEAN",
                    "answers", List.of(
                        Map.of("answer", " ", "isCorrect", true),
                        Map.of("answer", "False", "isCorrect", false)
                    )
                ))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createQuestionReturnsBadRequestWhenAnswerCorrectFlagIsMissing() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuiz(user.id(), "quizbad003", "Invalid quiz", "Checks validation.", LocalDateTime.now());

        mockMvc.perform(post(CREATE_QUESTION_ENDPOINT, "quizbad003")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "question": "Is this valid?",
                        "questionType": "BOOLEAN",
                        "answers": [
                            { "answer": "True" },
                            { "answer": "False", "isCorrect": false }
                        ]
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createQuestionReturnsBadRequestWhenQuestionTypeIsMissing() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuiz(user.id(), "quizbad004", "Invalid quiz", "Checks validation.", LocalDateTime.now());

        mockMvc.perform(post(CREATE_QUESTION_ENDPOINT, "quizbad004")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "question": "Is this valid?",
                        "answers": [
                            { "answer": "True", "isCorrect": true },
                            { "answer": "False", "isCorrect": false }
                        ]
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createQuestionReturnsBadRequestWhenAnswerCountDoesNotMatchQuestionType() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuiz(user.id(), "quizbad005", "Invalid quiz", "Checks validation.", LocalDateTime.now());

        mockMvc.perform(post(CREATE_QUESTION_ENDPOINT, "quizbad005")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "question", "Is this valid?",
                    "questionType", "MULTIPLE_CHOICE",
                    "answers", List.of(
                        Map.of("answer", "One", "isCorrect", true),
                        Map.of("answer", "Two", "isCorrect", false)
                    )
                ))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createQuestionReturnsBadRequestWhenThereIsNotExactlyOneCorrectAnswer() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuiz(user.id(), "quizbad006", "Invalid quiz", "Checks validation.", LocalDateTime.now());

        mockMvc.perform(post(CREATE_QUESTION_ENDPOINT, "quizbad006")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "question", "Is this valid?",
                    "questionType", "BOOLEAN",
                    "answers", List.of(
                        Map.of("answer", "True", "isCorrect", true),
                        Map.of("answer", "False", "isCorrect", true)
                    )
                ))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createQuestionReturnsBadRequestWhenAnswersContainsNullItem() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuiz(user.id(), "quizbad007", "Invalid quiz", "Checks validation.", LocalDateTime.now());

        mockMvc.perform(post(CREATE_QUESTION_ENDPOINT, "quizbad007")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "question": "Is this valid?",
                        "questionType": "BOOLEAN",
                        "answers": [
                            null,
                            { "answer": "False", "isCorrect": false }
                        ]
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createQuestionReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(post(CREATE_QUESTION_ENDPOINT, "quizbad008")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBooleanQuestionJson()))
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

    private void createQuestion(
        Long quizId,
        String publicId,
        String questionText,
        String questionType,
        int position
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO quiz_question (
                quiz_id,
                public_id,
                question_text,
                question_type,
                position
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            quizId,
            publicId,
            questionText,
            questionType,
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

    private Long countQuestions(Long quizId, String questionText) {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM quiz_question
            WHERE quiz_id = ? AND question_text = ?
            """,
            Long.class,
            quizId,
            questionText
        );
    }

    private LocalDateTime quizUpdatedAt(Long quizId) {
        return jdbcTemplate.queryForObject(
            "SELECT updated_at FROM quiz WHERE id = ?",
            LocalDateTime.class,
            quizId
        );
    }

    private String validBooleanQuestionJson() {
        return """
            {
                "question": "Is this valid?",
                "questionType": "BOOLEAN",
                "answers": [
                    { "answer": "True", "isCorrect": true },
                    { "answer": "False", "isCorrect": false }
                ]
            }
            """;
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record TestUser(
        Long id,
        String accessToken
    ) {}
}

package com.synapse.backend.quiz;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class QuizGetIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
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
    void doesNotGenerateQuiz() {
        verifyNoInteractions(llmClient);
    }

    @Test
    void getQuizReturnsCurrentUsersQuizWithQuestionsAndAnswersOrdered() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");

        Long quizId = createQuiz(
            user.id(),
            "quizfull01",
            "Sequence quiz",
            "Checks sequence diagram concepts.",
            "2026-01-05 10:00:00"
        );
        Long otherQuizId = createQuiz(
            otherUser.id(),
            "quizhide01",
            "Hidden quiz",
            "This quiz belongs to another user.",
            "2026-01-06 10:00:00"
        );

        Long secondQuestionId = createQuestion(
            quizId,
            "question02",
            "Second question",
            "BOOLEAN",
            1,
            "2026-01-05 10:02:00"
        );
        Long firstQuestionId = createQuestion(
            quizId,
            "question01",
            "First question",
            "MULTIPLE_CHOICE",
            0,
            "2026-01-05 10:01:00"
        );
        createQuestion(otherQuizId, "question99", "Hidden question", "BOOLEAN", 0, "2026-01-06 10:01:00");

        createAnswer(
            firstQuestionId,
            "answer0002",
            "Second answer",
            false,
            1,
            "2026-01-05 10:01:02"
        );
        createAnswer(
            firstQuestionId,
            "answer0001",
            "First answer",
            true,
            0,
            "2026-01-05 10:01:01"
        );
        createAnswer(
            secondQuestionId,
            "answer0004",
            "False",
            false,
            1,
            "2026-01-05 10:02:02"
        );
        createAnswer(
            secondQuestionId,
            "answer0003",
            "True",
            true,
            0,
            "2026-01-05 10:02:01"
        );

        mockMvc.perform(get(QUIZ_ENDPOINT, "quizfull01")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("quizfull01"))
            .andExpect(jsonPath("$.title").value("Sequence quiz"))
            .andExpect(jsonPath("$.description").value("Checks sequence diagram concepts."))
            .andExpect(jsonPath("$.createdAt").value("2026-01-05T10:00:00"))
            .andExpect(jsonPath("$.questions", hasSize(2)))
            .andExpect(jsonPath("$.questions[0].id").value("question01"))
            .andExpect(jsonPath("$.questions[0].text").value("First question"))
            .andExpect(jsonPath("$.questions[0].questionType").value("MULTIPLE_CHOICE"))
            .andExpect(jsonPath("$.questions[0].createdAt").value("2026-01-05T10:01:00"))
            .andExpect(jsonPath("$.questions[0].answers", hasSize(2)))
            .andExpect(jsonPath("$.questions[0].answers[0].id").value("answer0001"))
            .andExpect(jsonPath("$.questions[0].answers[0].text").value("First answer"))
            .andExpect(jsonPath("$.questions[0].answers[0].correct").value(true))
            .andExpect(jsonPath("$.questions[0].answers[0].createdAt").value("2026-01-05T10:01:01"))
            .andExpect(jsonPath("$.questions[0].answers[1].id").value("answer0002"))
            .andExpect(jsonPath("$.questions[0].answers[1].text").value("Second answer"))
            .andExpect(jsonPath("$.questions[0].answers[1].correct").value(false))
            .andExpect(jsonPath("$.questions[1].id").value("question02"))
            .andExpect(jsonPath("$.questions[1].questionType").value("BOOLEAN"))
            .andExpect(jsonPath("$.questions[1].answers[0].id").value("answer0003"))
            .andExpect(jsonPath("$.questions[1].answers[0].text").value("True"))
            .andExpect(jsonPath("$.questions[1].answers[1].id").value("answer0004"))
            .andExpect(jsonPath("$.questions[1].answers[1].text").value("False"));
    }

    @Test
    void getQuizDoesNotReturnQuizOwnedByAnotherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        createQuiz(
            otherUser.id(),
            "quizhide01",
            "Hidden quiz",
            "This quiz belongs to another user.",
            "2026-01-06 10:00:00"
        );

        mockMvc.perform(get(QUIZ_ENDPOINT, "quizhide01")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: quizhide01"));
    }

    @Test
    void getQuizReturnsNotFoundWhenQuizDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(QUIZ_ENDPOINT, "missing001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: missing001"));
    }

    @Test
    void getQuizReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get(QUIZ_ENDPOINT, "quizfull01"))
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
        String createdAt
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
            createdAt,
            createdAt
        );
    }

    private Long createQuestion(
        Long quizId,
        String publicId,
        String questionText,
        String questionType,
        int position,
        String createdAt
    ) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO quiz_question (
                quiz_id,
                public_id,
                question_text,
                question_type,
                position,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?::timestamp, ?::timestamp)
            RETURNING id
            """,
            Long.class,
            quizId,
            publicId,
            questionText,
            questionType,
            position,
            createdAt,
            createdAt
        );
    }

    private void createAnswer(
        Long questionId,
        String publicId,
        String answerText,
        boolean correct,
        int position,
        String createdAt
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO quiz_answer (
                question_id,
                public_id,
                answer_text,
                is_correct,
                position,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?::timestamp, ?::timestamp)
            """,
            questionId,
            publicId,
            answerText,
            correct,
            position,
            createdAt,
            createdAt
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

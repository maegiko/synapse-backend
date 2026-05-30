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
class QuizListIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String LIST_ENDPOINT = "/api/quiz/list";
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
    void getAllQuizzesReturnsCurrentUsersQuizzesWithQuestionsAndAnswersOrdered() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");

        Long olderQuizId = createQuiz(
            user.id(),
            "quizold001",
            "Older quiz",
            "Older description",
            "2026-01-02 09:00:00"
        );
        Long newerQuizId = createQuiz(
            user.id(),
            "quiznew001",
            "Newer quiz",
            "Newer description",
            "2026-01-03 09:00:00"
        );
        Long otherQuizId = createQuiz(
            otherUser.id(),
            "quizoth001",
            "Private quiz",
            "Private description",
            "2026-01-04 09:00:00"
        );

        createQuestion(olderQuizId, "oldq000001", "Older first question", "BOOLEAN", 0, "2026-01-02 09:01:00");

        Long newerSecondQuestionId = createQuestion(
            newerQuizId,
            "newq000002",
            "Second question",
            "BOOLEAN",
            1,
            "2026-01-03 09:02:00"
        );
        Long newerFirstQuestionId = createQuestion(
            newerQuizId,
            "newq000001",
            "First question",
            "MULTIPLE_CHOICE",
            0,
            "2026-01-03 09:01:00"
        );
        createAnswer(
            newerFirstQuestionId,
            "newa000002",
            "Second answer",
            false,
            1,
            "2026-01-03 09:01:02"
        );
        createAnswer(
            newerFirstQuestionId,
            "newa000001",
            "First answer",
            true,
            0,
            "2026-01-03 09:01:01"
        );
        createAnswer(
            newerSecondQuestionId,
            "newa000003",
            "True",
            false,
            0,
            "2026-01-03 09:02:01"
        );
        createQuestion(otherQuizId, "othq000001", "Hidden question", "BOOLEAN", 0, "2026-01-04 09:01:00");

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes", hasSize(2)))
            .andExpect(jsonPath("$.quizzes[0].id").value("quiznew001"))
            .andExpect(jsonPath("$.quizzes[0].title").value("Newer quiz"))
            .andExpect(jsonPath("$.quizzes[0].description").value("Newer description"))
            .andExpect(jsonPath("$.quizzes[0].createdAt").value("2026-01-03T09:00:00"))
            .andExpect(jsonPath("$.quizzes[0].questions", hasSize(2)))
            .andExpect(jsonPath("$.quizzes[0].questions[0].id").value("newq000001"))
            .andExpect(jsonPath("$.quizzes[0].questions[0].text").value("First question"))
            .andExpect(jsonPath("$.quizzes[0].questions[0].questionType").value("MULTIPLE_CHOICE"))
            .andExpect(jsonPath("$.quizzes[0].questions[0].createdAt").value("2026-01-03T09:01:00"))
            .andExpect(jsonPath("$.quizzes[0].questions[0].answers", hasSize(2)))
            .andExpect(jsonPath("$.quizzes[0].questions[0].answers[0].id").value("newa000001"))
            .andExpect(jsonPath("$.quizzes[0].questions[0].answers[0].text").value("First answer"))
            .andExpect(jsonPath("$.quizzes[0].questions[0].answers[0].correct").value(true))
            .andExpect(jsonPath("$.quizzes[0].questions[0].answers[0].createdAt").value("2026-01-03T09:01:01"))
            .andExpect(jsonPath("$.quizzes[0].questions[0].answers[1].id").value("newa000002"))
            .andExpect(jsonPath("$.quizzes[0].questions[1].id").value("newq000002"))
            .andExpect(jsonPath("$.quizzes[0].questions[1].answers[0].id").value("newa000003"))
            .andExpect(jsonPath("$.quizzes[1].id").value("quizold001"))
            .andExpect(jsonPath("$.quizzes[1].title").value("Older quiz"));
    }

    @Test
    void getAllQuizzesReturnsEmptyListWhenUserHasNoQuizzes() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes", hasSize(0)));
    }

    @Test
    void getAllQuizzesReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get(LIST_ENDPOINT))
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

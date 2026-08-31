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
    void getAllQuizzesReturnsCurrentUsersQuizzesWithQuestionPreviewsOrdered() throws Exception {
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
            .andExpect(jsonPath("$.quizzes[0].questions[0].createdAt").value("2026-01-03T09:01:00"))
            .andExpect(jsonPath("$.quizzes[0].questions[0].questionType").doesNotExist())
            .andExpect(jsonPath("$.quizzes[0].questions[0].answers").doesNotExist())
            .andExpect(jsonPath("$.quizzes[0].questions[1].id").value("newq000002"))
            .andExpect(jsonPath("$.quizzes[0].questions[1].answers").doesNotExist())
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
    void getAllQuizzesUsesDefaultPaginationWhenNoParametersAreSupplied() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuizzes(user.id(), "Quiz", 25);

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes", hasSize(20)))
            .andExpect(jsonPath("$.quizzes[0].title").value("Quiz 25"))
            .andExpect(jsonPath("$.quizzes[19].title").value("Quiz 6"))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(25))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void getAllQuizzesReturnsTheRequestedPageAndSize() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuizzes(user.id(), "Quiz", 5);

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("page", "1")
                .param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes", hasSize(2)))
            .andExpect(jsonPath("$.quizzes[0].title").value("Quiz 3"))
            .andExpect(jsonPath("$.quizzes[1].title").value("Quiz 2"))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void getAllQuizzesReturnsAnEmptyPageBeyondTheEnd() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuizzes(user.id(), "Quiz", 3);

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("page", "5")
                .param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes", hasSize(0)))
            .andExpect(jsonPath("$.page").value(5))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void getAllQuizzesSearchesTitlesCaseInsensitivelyAndPartially() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long systemsQuizId = createQuiz(user.id(), "quizsrc001", "System Dynamics", "A quiz", "2026-01-01 09:00:00");
        createQuiz(user.id(), "quizsrc002", "Nervous system", "A quiz", "2026-01-02 09:00:00");
        createQuiz(user.id(), "quizsrc003", "Enzymes", "A quiz", "2026-01-03 09:00:00");
        createQuestion(systemsQuizId, "srcq000001", "What is a stock?", "BOOLEAN", 0, "2026-01-01 09:01:00");

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("query", "SYSTEM")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes", hasSize(2)))
            .andExpect(jsonPath("$.quizzes[0].title").value("Nervous system"))
            .andExpect(jsonPath("$.quizzes[1].title").value("System Dynamics"))
            .andExpect(jsonPath("$.quizzes[1].questions", hasSize(1)))
            .andExpect(jsonPath("$.quizzes[1].questions[0].text").value("What is a stock?"))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void getAllQuizzesTrimsTheSearchQuery() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuiz(user.id(), "quiztrm001", "Enzymes", "A quiz", "2026-01-01 09:00:00");
        createQuiz(user.id(), "quiztrm002", "Cells", "A quiz", "2026-01-02 09:00:00");

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("query", "  enzy  ")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes", hasSize(1)))
            .andExpect(jsonPath("$.quizzes[0].title").value("Enzymes"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAllQuizzesTreatsABlankSearchQueryAsNoSearch() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuizzes(user.id(), "Quiz", 3);

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("query", "   ")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes", hasSize(3)))
            .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void getAllQuizzesReturnsAnEmptyPageWhenTheSearchMatchesNothing() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuizzes(user.id(), "Quiz", 3);

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("query", "photosynthesis")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes", hasSize(0)))
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.totalPages").value(0))
            .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void getAllQuizzesBreaksCreatedAtTiesByNewestIdFirst() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createQuiz(user.id(), "quiztie001", "First saved", "A quiz", "2026-01-01 09:00:00");
        createQuiz(user.id(), "quiztie002", "Second saved", "A quiz", "2026-01-01 09:00:00");
        createQuiz(user.id(), "quiztie003", "Third saved", "A quiz", "2026-01-01 09:00:00");

        mockMvc.perform(get(LIST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes[0].id").value("quiztie003"))
            .andExpect(jsonPath("$.quizzes[1].id").value("quiztie002"))
            .andExpect(jsonPath("$.quizzes[2].id").value("quiztie001"));

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("page", "1")
                .param("size", "1")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes", hasSize(1)))
            .andExpect(jsonPath("$.quizzes[0].id").value("quiztie002"));
    }

    @Test
    void getAllQuizzesOnlySearchesQuizzesOwnedByTheAuthenticatedUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        createQuiz(currentUser.id(), "quizown001", "Shared title mine", "A quiz", "2026-01-01 09:00:00");
        createQuiz(otherUser.id(), "quizown002", "Shared title theirs", "A quiz", "2026-01-02 09:00:00");

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("query", "shared title")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizzes", hasSize(1)))
            .andExpect(jsonPath("$.quizzes[0].title").value("Shared title mine"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAllQuizzesRejectsANegativePage() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("page", "-1")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("page: must be greater than or equal to 0"));
    }

    @Test
    void getAllQuizzesRejectsASizeBelowOne() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("size", "0")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("size: must be greater than or equal to 1"));
    }

    @Test
    void getAllQuizzesRejectsASizeAboveTheMaximum() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(LIST_ENDPOINT)
                .param("size", "101")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("size: must be less than or equal to 100"));
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

    private void createQuizzes(Long userId, String titlePrefix, int count) {
        for (int i = 1; i <= count; i++) {
            createQuiz(
                userId,
                String.format("quizpg%04d", i),
                titlePrefix + " " + i,
                "Description " + i,
                String.format("2026-01-01 09:%02d:00", i)
            );
        }
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

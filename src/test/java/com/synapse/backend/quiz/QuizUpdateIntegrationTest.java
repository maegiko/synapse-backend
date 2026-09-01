package com.synapse.backend.quiz;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
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
class QuizUpdateIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String QUIZ_ENDPOINT = "/api/quiz/{quizId}";
    private static final String DIFFICULTY_ENDPOINT = "/api/quiz/{quizId}/difficulty";
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
    void updateQuizUpdatesOnlyTitleAdvancesTimestampAndKeepsQuestionsAndDifficulty() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 2, 1, 9, 0);
        Long quizId = createQuiz(user.id(), "quizupd001", "Old title", "The description.", originalUpdatedAt);
        setDifficulty(user, "quizupd001", 3);
        Long questionId = createQuestion(quizId, "question01", "Existing question", "BOOLEAN", 0);
        createAnswer(questionId, "answer0001", "True", true, 0);
        createAnswer(questionId, "answer0002", "False", false, 1);

        Map<String, Object> body = new HashMap<>();
        body.put("title", "Systems quiz");

        mockMvc.perform(patch(QUIZ_ENDPOINT, "quizupd001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("quizupd001"))
            .andExpect(jsonPath("$.title").value("Systems quiz"))
            .andExpect(jsonPath("$.description").value("The description."))
            .andExpect(jsonPath("$.difficulty").value(3))
            .andExpect(jsonPath("$.questions", hasSize(1)))
            .andExpect(jsonPath("$.questions[0].id").value("question01"))
            .andExpect(jsonPath("$.questions[0].answers", hasSize(2)));

        assertEquals("Systems quiz", quizColumn(quizId, "title"));
        assertEquals("The description.", quizColumn(quizId, "description"));
        assertEquals(3, quizDifficulty(quizId));
        assertTrue(quizUpdatedAt(quizId).isAfter(originalUpdatedAt));
    }

    @Test
    void updateQuizUpdatesBothFieldsAndTrimsThem() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizupd002", "Old title", "Old description.", LocalDateTime.of(2026, 2, 2, 9, 0));

        mockMvc.perform(patch(QUIZ_ENDPOINT, "quizupd002")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "title", "  Systems quiz  ",
                    "description", "  A new description.  "
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Systems quiz"))
            .andExpect(jsonPath("$.description").value("A new description."));

        assertEquals("Systems quiz", quizColumn(quizId, "title"));
        assertEquals("A new description.", quizColumn(quizId, "description"));
    }

    @Test
    void updateQuizStoresABlankDescriptionAsNull() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizupd003", "The title", "Old description.", LocalDateTime.of(2026, 2, 3, 9, 0));

        Map<String, Object> body = new HashMap<>();
        body.put("description", "   ");

        mockMvc.perform(patch(QUIZ_ENDPOINT, "quizupd003")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").isEmpty());

        assertNull(quizColumn(quizId, "description"));
    }

    @Test
    void updateQuizRejectsAnEmptyRequest() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizupd004", "The title", "The description.", LocalDateTime.of(2026, 2, 4, 9, 0));

        mockMvc.perform(patch(QUIZ_ENDPOINT, "quizupd004")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("At least one of title, description, or pinned must be supplied."));

        assertEquals("The title", quizColumn(quizId, "title"));
    }

    @Test
    void updateQuizPinsAndUnpinsAQuiz() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizpin001", "The title", "The description.", LocalDateTime.of(2026, 2, 7, 9, 0));

        Map<String, Object> pin = new HashMap<>();
        pin.put("pinned", true);

        mockMvc.perform(patch(QUIZ_ENDPOINT, "quizpin001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("The title"))
            .andExpect(jsonPath("$.pinned").value(true));

        assertEquals(Boolean.TRUE, quizPinned(quizId));

        Map<String, Object> unpin = new HashMap<>();
        unpin.put("pinned", false);

        mockMvc.perform(patch(QUIZ_ENDPOINT, "quizpin001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(unpin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pinned").value(false));

        assertEquals(Boolean.FALSE, quizPinned(quizId));
    }

    @Test
    void updateQuizAcceptsARequestWithOnlyThePinFlag() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizpin002", "The title", "The description.", LocalDateTime.of(2026, 2, 8, 9, 0));

        mockMvc.perform(patch(QUIZ_ENDPOINT, "quizpin002")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pinned\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pinned").value(true));

        assertEquals("The title", quizColumn(quizId, "title"));
        assertEquals(Boolean.TRUE, quizPinned(quizId));
    }

    @Test
    void updateQuizDoesNotChangeAnotherUsersPinState() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        Long otherQuizId = createQuiz(
            otherUser.id(),
            "quizpin003",
            "Private title",
            "Private description.",
            LocalDateTime.of(2026, 2, 9, 9, 0)
        );

        mockMvc.perform(patch(QUIZ_ENDPOINT, "quizpin003")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pinned\":true}"))
            .andExpect(status().isNotFound());

        assertEquals(Boolean.FALSE, quizPinned(otherQuizId));
    }

    @Test
    void updateQuizRejectsABlankTitle() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizupd005", "The title", "The description.", LocalDateTime.of(2026, 2, 5, 9, 0));

        Map<String, Object> body = new HashMap<>();
        body.put("title", "   ");

        mockMvc.perform(patch(QUIZ_ENDPOINT, "quizupd005")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("title: must not be blank"));

        assertEquals("The title", quizColumn(quizId, "title"));
    }

    @Test
    void updateQuizReturnsNotFoundWhenQuizDoesNotExist() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(patch(QUIZ_ENDPOINT, "missing001")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "Systems quiz"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: missing001"));
    }

    @Test
    void updateQuizDoesNotUpdateQuizOwnedByAnotherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        Long otherQuizId = createQuiz(
            otherUser.id(),
            "quizupd006",
            "Private title",
            "Private description.",
            LocalDateTime.of(2026, 2, 6, 9, 0)
        );

        mockMvc.perform(patch(QUIZ_ENDPOINT, "quizupd006")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "Stolen title"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Quiz not found: quizupd006"));

        assertEquals("Private title", quizColumn(otherQuizId, "title"));
    }

    @Test
    void updateQuizReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(patch(QUIZ_ENDPOINT, "quizupd007")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "Systems quiz"))))
            .andExpect(status().isUnauthorized());
    }

    private void setDifficulty(TestUser user, String quizPublicId, int difficulty) throws Exception {
        mockMvc.perform(put(DIFFICULTY_ENDPOINT, quizPublicId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("difficulty", difficulty))))
            .andExpect(status().isNoContent());
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

    private Long createQuiz(Long userId, String publicId, String title, String description, LocalDateTime updatedAt) {
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

    private String quizColumn(Long quizId, String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM quiz WHERE id = ?", String.class, quizId);
    }

    private Boolean quizPinned(Long quizId) {
        return jdbcTemplate.queryForObject("SELECT pinned FROM quiz WHERE id = ?", Boolean.class, quizId);
    }

    private Integer quizDifficulty(Long quizId) {
        return jdbcTemplate.queryForObject("SELECT difficulty FROM quiz WHERE id = ?", Integer.class, quizId);
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

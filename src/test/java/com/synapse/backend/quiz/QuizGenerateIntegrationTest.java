package com.synapse.backend.quiz;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.quiz.dto.GenerateQuizRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class QuizGenerateIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String GENERATE_ENDPOINT = "/api/quiz/generate";
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

    @Test
    void generateQuizReturnsPersistedQuizWithQuestionsAnswersAndAccurateTimestamps() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn(validQuizJson());
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "Sequence diagrams", "Sequence diagrams show object interactions.");
        createKeypoint(note.id(), 0, "Lifelines represent participants over time.");
        createConcept(note.id(), 0, "Message", "A message represents communication between participants.");
        createImportantTerm(note.id(), 0, "activation");

        MvcResult result = mockMvc.perform(post(GENERATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GenerateQuizRequest(note.publicId())))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isString())
            .andExpect(jsonPath("$.title").value("Sequence Diagram Fundamentals"))
            .andExpect(jsonPath("$.description").value("Checks understanding of lifelines, messages, and activations."))
            .andExpect(jsonPath("$.createdAt").isString())
            .andExpect(jsonPath("$.questions", hasSize(10)))
            .andExpect(jsonPath("$.questions[0].id").isString())
            .andExpect(jsonPath("$.questions[0].text").value("What does a lifeline represent in a sequence diagram?"))
            .andExpect(jsonPath("$.questions[0].questionType").value("MULTIPLE_CHOICE"))
            .andExpect(jsonPath("$.questions[0].createdAt").isString())
            .andExpect(jsonPath("$.questions[0].answers", hasSize(4)))
            .andExpect(jsonPath("$.questions[0].answers[0].id").isString())
            .andExpect(jsonPath("$.questions[0].answers[0].text").value("A participant's existence over time"))
            .andExpect(jsonPath("$.questions[0].answers[0].correct").value(true))
            .andExpect(jsonPath("$.questions[0].answers[0].createdAt").isString())
            .andExpect(jsonPath("$.questions[1].questionType").value("BOOLEAN"))
            .andExpect(jsonPath("$.questions[1].answers", hasSize(2)))
            .andExpect(jsonPath("$.questions[1].answers[0].text").value("True"))
            .andExpect(jsonPath("$.questions[1].answers[1].text").value("False"))
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String quizPublicId = response.get("id").asString();

        Map<String, Object> quiz = jdbcTemplate.queryForMap(
            """
            SELECT id, public_id, user_id, note_id, title, description, source_type, created_at
            FROM quiz
            WHERE public_id = ?
            """,
            quizPublicId
        );
        Long quizId = ((Number) quiz.get("id")).longValue();

        assertEquals(user.id(), quiz.get("user_id"));
        assertEquals(note.id(), quiz.get("note_id"));
        assertEquals("Sequence Diagram Fundamentals", quiz.get("title"));
        assertEquals("Checks understanding of lifelines, messages, and activations.", quiz.get("description"));
        assertEquals("NOTE", quiz.get("source_type"));
        assertSameTimestamp(quiz.get("created_at"), response.get("createdAt").asString());

        List<Map<String, Object>> questions = jdbcTemplate.queryForList(
            """
            SELECT id, public_id, quiz_id, question_text, question_type, position, created_at
            FROM quiz_question
            WHERE quiz_id = ?
            ORDER BY position ASC
            """,
            quizId
        );

        assertEquals(10, questions.size());
        Map<String, Object> firstQuestion = questions.get(0);
        Long firstQuestionId = ((Number) firstQuestion.get("id")).longValue();
        assertEquals(response.get("questions").get(0).get("id").asString(), firstQuestion.get("public_id"));
        assertEquals(quizId, firstQuestion.get("quiz_id"));
        assertEquals("What does a lifeline represent in a sequence diagram?", firstQuestion.get("question_text"));
        assertEquals("MULTIPLE_CHOICE", firstQuestion.get("question_type"));
        assertEquals(0, ((Number) firstQuestion.get("position")).intValue());
        assertSameTimestamp(
            firstQuestion.get("created_at"),
            response.get("questions").get(0).get("createdAt").asString()
        );

        List<Map<String, Object>> firstAnswers = jdbcTemplate.queryForList(
            """
            SELECT public_id, question_id, answer_text, is_correct, position, created_at
            FROM quiz_answer
            WHERE question_id = ?
            ORDER BY position ASC
            """,
            firstQuestionId
        );

        assertEquals(4, firstAnswers.size());
        assertAnswer(
            firstAnswers.get(0),
            response.get("questions").get(0).get("answers").get(0),
            firstQuestionId,
            "A participant's existence over time",
            true,
            0
        );
        assertAnswer(
            firstAnswers.get(1),
            response.get("questions").get(0).get("answers").get(1),
            firstQuestionId,
            "The final answer in a quiz",
            false,
            1
        );
    }

    @Test
    void generateQuizReturnsNotFoundAndDoesNotCallLlmWhenNoteBelongsToAnotherUser() throws Exception {
        TestUser currentUser = register("Kenneth", "kenneth@example.com");
        TestUser otherUser = register("Ada", "ada@example.com");
        TestNote otherUsersNote = createNote(otherUser.id(), "Private note", "This should stay private.");

        mockMvc.perform(post(GENERATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GenerateQuizRequest(otherUsersNote.publicId())))
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Requested note not found."));

        verifyNoInteractions(llmClient);
        assertNoQuizzesWereSaved();
    }

    @Test
    void generateQuizReturnsBadGatewayAndDoesNotSaveWhenLlmResponseIsInvalidJson() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn("not json");
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "Sequence diagrams", "Sequence diagrams show object interactions.");

        mockMvc.perform(post(GENERATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GenerateQuizRequest(note.publicId())))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.message").value("Failed to parse LLM response"));

        assertNoQuizzesWereSaved();
    }

    @Test
    void generateQuizReturnsBadGatewayAndDoesNotSaveWhenLlmQuizDoesNotMatchExpectedStructure() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn("""
            {
              "title": "Too short",
              "description": "This quiz does not have enough questions.",
              "questions": [
                {
                  "questionText": "Only one question?",
                  "questionType": "BOOLEAN",
                  "answers": [
                    { "answerText": "True", "correct": true },
                    { "answerText": "False", "correct": false }
                  ]
                }
              ]
            }
            """);
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "Sequence diagrams", "Sequence diagrams show object interactions.");

        mockMvc.perform(post(GENERATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GenerateQuizRequest(note.publicId())))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadGateway());

        assertNoQuizzesWereSaved();
    }

    @Test
    void generateQuizReturnsBadRequestWhenNoteIdIsMissing() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(post(GENERATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("noteId: must not be null"));

        verifyNoInteractions(llmClient);
        assertNoQuizzesWereSaved();
    }

    @Test
    void generateQuizReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(post(GENERATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GenerateQuizRequest(
                    UUID.fromString("00000000-0000-0000-0000-000000000001")
                ))))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(llmClient);
        assertNoQuizzesWereSaved();
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

    private TestNote createNote(Long userId, String title, String overview) {
        UUID publicId = UUID.randomUUID();
        Long id = jdbcTemplate.queryForObject(
            """
            INSERT INTO note (user_id, public_id, title, overview)
            VALUES (?, ?::uuid, ?, ?)
            RETURNING id
            """,
            Long.class,
            userId,
            publicId,
            title,
            overview
        );

        return new TestNote(id, publicId);
    }

    private void createKeypoint(Long noteId, int position, String keypoint) {
        jdbcTemplate.update(
            "INSERT INTO note_keypoint (note_id, position, keypoint) VALUES (?, ?, ?)",
            noteId,
            position,
            keypoint
        );
    }

    private void createConcept(Long noteId, int position, String name, String explanation) {
        jdbcTemplate.update(
            "INSERT INTO note_concept (note_id, position, name, explanation) VALUES (?, ?, ?, ?)",
            noteId,
            position,
            name,
            explanation
        );
    }

    private void createImportantTerm(Long noteId, int position, String term) {
        jdbcTemplate.update(
            "INSERT INTO note_important_term (note_id, position, term) VALUES (?, ?, ?)",
            noteId,
            position,
            term
        );
    }

    private void assertAnswer(
        Map<String, Object> answer,
        JsonNode responseAnswer,
        Long questionId,
        String text,
        boolean correct,
        int position
    ) {
        assertNotNull(answer);
        assertEquals(responseAnswer.get("id").asString(), answer.get("public_id"));
        assertEquals(questionId, answer.get("question_id"));
        assertEquals(text, answer.get("answer_text"));
        assertEquals(correct, answer.get("is_correct"));
        assertEquals(position, ((Number) answer.get("position")).intValue());
        assertSameTimestamp(answer.get("created_at"), responseAnswer.get("createdAt").asString());
    }

    private void assertSameTimestamp(Object databaseValue, String responseValue) {
        assertEquals(toLocalDateTime(databaseValue), LocalDateTime.parse(responseValue));
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime)
            return localDateTime;

        if (value instanceof Timestamp timestamp)
            return timestamp.toLocalDateTime();

        throw new AssertionError("Unsupported timestamp type: " + value.getClass());
    }

    private void assertNoQuizzesWereSaved() {
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quiz", Long.class));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quiz_question", Long.class));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quiz_answer", Long.class));
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String validQuizJson() {
        return """
            {
              "title": "Sequence Diagram Fundamentals",
              "description": "Checks understanding of lifelines, messages, and activations.",
              "questions": [
                {
                  "questionText": "What does a lifeline represent in a sequence diagram?",
                  "questionType": "MULTIPLE_CHOICE",
                  "answers": [
                    { "answerText": "A participant's existence over time", "correct": true },
                    { "answerText": "The final answer in a quiz", "correct": false },
                    { "answerText": "A database migration step", "correct": false },
                    { "answerText": "A package dependency", "correct": false }
                  ]
                },
                {
                  "questionText": "A message represents communication between participants.",
                  "questionType": "BOOLEAN",
                  "answers": [
                    { "answerText": "True", "correct": true },
                    { "answerText": "False", "correct": false }
                  ]
                },
                {
                  "questionText": "What does an activation usually show?",
                  "questionType": "MULTIPLE_CHOICE",
                  "answers": [
                    { "answerText": "The time a participant is performing an action", "correct": true },
                    { "answerText": "The title of the diagram", "correct": false },
                    { "answerText": "The file extension of the note", "correct": false },
                    { "answerText": "The list of imported classes", "correct": false }
                  ]
                },
                {
                  "questionText": "Sequence diagrams can show object interactions over time.",
                  "questionType": "BOOLEAN",
                  "answers": [
                    { "answerText": "True", "correct": true },
                    { "answerText": "False", "correct": false }
                  ]
                },
                {
                  "questionText": "Which topic is central to sequence diagrams?",
                  "questionType": "MULTIPLE_CHOICE",
                  "answers": [
                    { "answerText": "Object interactions", "correct": true },
                    { "answerText": "Password hashing", "correct": false },
                    { "answerText": "Database indexing", "correct": false },
                    { "answerText": "CSS layout", "correct": false }
                  ]
                },
                {
                  "questionText": "A lifeline is unrelated to time in a sequence diagram.",
                  "questionType": "BOOLEAN",
                  "answers": [
                    { "answerText": "True", "correct": false },
                    { "answerText": "False", "correct": true }
                  ]
                },
                {
                  "questionText": "What does a sequence diagram emphasize?",
                  "questionType": "MULTIPLE_CHOICE",
                  "answers": [
                    { "answerText": "The order of interactions", "correct": true },
                    { "answerText": "The color palette of an app", "correct": false },
                    { "answerText": "The size of a PDF file", "correct": false },
                    { "answerText": "The number of database tables", "correct": false }
                  ]
                },
                {
                  "questionText": "Messages and lifelines are both relevant sequence diagram concepts.",
                  "questionType": "BOOLEAN",
                  "answers": [
                    { "answerText": "True", "correct": true },
                    { "answerText": "False", "correct": false }
                  ]
                },
                {
                  "questionText": "Which answer best connects lifelines and messages?",
                  "questionType": "MULTIPLE_CHOICE",
                  "answers": [
                    { "answerText": "Messages occur between participants represented by lifelines", "correct": true },
                    { "answerText": "Messages replace the need for participants", "correct": false },
                    { "answerText": "Lifelines store generated quiz answers", "correct": false },
                    { "answerText": "Messages are database foreign keys", "correct": false }
                  ]
                },
                {
                  "questionText": "Why might activations matter when interpreting a sequence diagram?",
                  "questionType": "MULTIPLE_CHOICE",
                  "answers": [
                    { "answerText": "They help show when a participant is carrying out behavior", "correct": true },
                    { "answerText": "They identify the user's authentication token", "correct": false },
                    { "answerText": "They define the HTTP route", "correct": false },
                    { "answerText": "They choose the database schema version", "correct": false }
                  ]
                }
              ]
            }
            """;
    }

    private record TestUser(
        Long id,
        String accessToken
    ) {}

    private record TestNote(
        Long id,
        UUID publicId
    ) {}
}

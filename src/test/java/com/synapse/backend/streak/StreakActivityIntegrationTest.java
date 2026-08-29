package com.synapse.backend.streak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.auth.dto.LoginRequest;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.flashcards.dto.generate.FlashcardGenerateNoteRequest;
import com.synapse.backend.quiz.dto.GenerateQuizRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class StreakActivityIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String LOGIN_ENDPOINT = "/api/auth/login";
    private static final String SUMMARY_ENDPOINT = "/api/notes/summarise";
    private static final String FLASHCARD_GENERATE_ENDPOINT = "/api/flashcards/generate";
    private static final String DECK_ENDPOINT = "/api/flashcards/{deckId}";
    private static final String DECK_COMPLETE_ENDPOINT = "/api/flashcards/{deckId}/complete";
    private static final String QUIZ_GENERATE_ENDPOINT = "/api/quiz/generate";
    private static final String QUIZ_SCORE_ENDPOINT = "/api/quiz/{quizId}/score";
    private static final String STREAK_ENDPOINT = "/api/user/streak";
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
    void summarisingANoteAwardsActivityForToday() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn(validSummaryJson());
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(multipart(SUMMARY_ENDPOINT)
                .file(textFile())
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        assertStreakIsOneDayEndingToday(user);
        assertEquals(List.of(today()), activityDates(user.id()));
    }

    @Test
    void generatingFlashcardsAwardsActivityForToday() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn(validFlashcardJson());
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "Biology notes", "An overview of cells.");

        mockMvc.perform(post(FLASHCARD_GENERATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FlashcardGenerateNoteRequest(note.publicId())))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        assertStreakIsOneDayEndingToday(user);
        assertEquals(List.of(today()), activityDates(user.id()));
    }

    @Test
    void generatingAQuizAwardsActivityForToday() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn(validQuizJson());
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "Biology notes", "An overview of cells.");

        mockMvc.perform(post(QUIZ_GENERATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GenerateQuizRequest(note.publicId())))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        assertStreakIsOneDayEndingToday(user);
        assertEquals(List.of(today()), activityDates(user.id()));
    }

    @Test
    void savingAQuizScoreAwardsActivityForToday() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizweek01", "Systems quiz");
        createQuestions(quizId, 3);

        mockMvc.perform(post(QUIZ_SCORE_ENDPOINT, "quizweek01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("score", 2)))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        assertStreakIsOneDayEndingToday(user);
        assertEquals(List.of(today()), activityDates(user.id()));
    }

    @Test
    void completingAFlashcardDeckAwardsActivityForToday() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeck(user.id(), "deckweek01", "Systems deck");

        mockMvc.perform(post(DECK_COMPLETE_ENDPOINT, "deckweek01")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        assertStreakIsOneDayEndingToday(user);
        assertEquals(List.of(today()), activityDates(user.id()));
    }

    @Test
    void severalQualifyingInteractionsOnTheSameDayCountAsOneStreakDay() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn(validSummaryJson());
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeck(user.id(), "deckweek02", "Systems deck");
        Long quizId = createQuiz(user.id(), "quizweek02", "Systems quiz");
        createQuestions(quizId, 3);

        mockMvc.perform(multipart(SUMMARY_ENDPOINT)
                .file(textFile())
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        mockMvc.perform(post(QUIZ_SCORE_ENDPOINT, "quizweek02")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("score", 1)))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        mockMvc.perform(post(DECK_COMPLETE_ENDPOINT, "deckweek02")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isNoContent());

        assertStreakIsOneDayEndingToday(user);
        assertEquals(List.of(today()), activityDates(user.id()));
    }

    @Test
    void openingAFlashcardDeckDoesNotAwardActivity() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        createDeck(user.id(), "deckweek03", "Systems deck");

        mockMvc.perform(get(DECK_ENDPOINT, "deckweek03")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk());

        assertNoStreak(user);
        assertEquals(List.of(), activityDates(user.id()));
    }

    @Test
    void loggingInDoesNotAwardActivity() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("kenneth@example.com", VALID_PASSWORD))))
            .andExpect(status().isOk());

        assertNoStreak(user);
        assertEquals(List.of(), activityDates(user.id()));
    }

    @Test
    void failedNoteGenerationDoesNotAwardActivity() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn("not json");
        TestUser user = register("Kenneth", "kenneth@example.com");

        mockMvc.perform(multipart(SUMMARY_ENDPOINT)
                .file(textFile())
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadGateway());

        assertNoStreak(user);
        assertEquals(List.of(), activityDates(user.id()));
    }

    @Test
    void failedFlashcardGenerationDoesNotAwardActivity() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn("not json");
        TestUser user = register("Kenneth", "kenneth@example.com");
        TestNote note = createNote(user.id(), "Biology notes", "An overview of cells.");

        mockMvc.perform(post(FLASHCARD_GENERATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FlashcardGenerateNoteRequest(note.publicId())))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadGateway());

        assertNoStreak(user);
        assertEquals(List.of(), activityDates(user.id()));
    }

    @Test
    void rejectedQuizScoreDoesNotAwardActivity() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizweek03", "Systems quiz");
        createQuestions(quizId, 2);

        mockMvc.perform(post(QUIZ_SCORE_ENDPOINT, "quizweek03")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("score", 3)))
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isBadRequest());

        assertNoStreak(user);
        assertEquals(List.of(), activityDates(user.id()));
    }

    private void assertStreakIsOneDayEndingToday(TestUser user) throws Exception {
        mockMvc.perform(get(STREAK_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(1))
            .andExpect(jsonPath("$.longestStreak").value(1))
            .andExpect(jsonPath("$.activeToday").value(true))
            .andExpect(jsonPath("$.lastActiveDate").value(today().toString()));
    }

    private void assertNoStreak(TestUser user) throws Exception {
        mockMvc.perform(get(STREAK_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(0))
            .andExpect(jsonPath("$.longestStreak").value(0))
            .andExpect(jsonPath("$.activeToday").value(false))
            .andExpect(jsonPath("$.lastActiveDate").isEmpty());
    }

    private LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
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
        String publicId = NanoIdUtils.randomNanoId(
            NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
            NanoIdUtils.DEFAULT_ALPHABET,
            10
        );
        Long id = jdbcTemplate.queryForObject(
            """
            INSERT INTO note (user_id, public_id, title, overview)
            VALUES (?, ?, ?, ?)
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

    private void createDeck(Long userId, String publicId, String title) {
        jdbcTemplate.update(
            """
            INSERT INTO flashcard_deck (user_id, title, source_type, public_id)
            VALUES (?, ?, 'NOTE', ?)
            """,
            userId,
            title,
            publicId
        );
    }

    private Long createQuiz(Long userId, String publicId, String title) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO quiz (user_id, public_id, title, description, source_type)
            VALUES (?, ?, ?, 'A saved quiz.', 'MANUAL')
            RETURNING id
            """,
            Long.class,
            userId,
            publicId,
            title
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

    private List<LocalDate> activityDates(Long userId) {
        return jdbcTemplate.query(
            """
            SELECT activity_date
            FROM streak_activity
            WHERE user_id = ?
            ORDER BY activity_date ASC
            """,
            (rs, rowNum) -> rs.getObject("activity_date", LocalDate.class),
            userId
        );
    }

    private MockMultipartFile textFile() {
        return new MockMultipartFile(
            "file",
            "notes.txt",
            "text/plain",
            "Behavioural modelling lecture notes".getBytes(StandardCharsets.UTF_8)
        );
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String validSummaryJson() {
        return """
            {
              "title": "Behavioural Modelling",
              "overview": "A short overview.",
              "keypoints": ["Models simplify behaviour."],
              "concepts": [
                {
                  "name": "Model",
                  "explanation": "A simplified representation."
                }
              ],
              "importantTerms": ["behaviour"]
            }
            """;
    }

    private String validFlashcardJson() {
        return """
            {
              "flashcards": [
                {
                  "title": "Mitochondria",
                  "answer": "The organelle that releases energy for the cell."
                }
              ]
            }
            """;
    }

    private String validQuizJson() {
        String question = """
            {
              "questionText": "A message represents communication between participants.",
              "questionType": "BOOLEAN",
              "answers": [
                { "answerText": "True", "correct": true },
                { "answerText": "False", "correct": false }
              ]
            }
            """;

        return """
            {
              "title": "Cell Fundamentals",
              "description": "Checks understanding of cells.",
              "questions": [%s]
            }
            """.formatted(String.join(",", Collections.nCopies(10, question)));
    }

    private record TestUser(
        Long id,
        String accessToken
    ) {}

    private record TestNote(
        Long id,
        String publicId
    ) {}
}

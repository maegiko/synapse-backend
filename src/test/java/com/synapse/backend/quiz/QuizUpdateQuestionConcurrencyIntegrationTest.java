package com.synapse.backend.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class QuizUpdateQuestionConcurrencyIntegrationTest extends PostgresIntegrationTest {

    private static final String QUESTION_ENDPOINT = "/api/quiz/{quizId}/questions/{questionId}";
    private static final String VALID_PASSWORD = "password123";

    private static final List<Map<String, Object>> ANSWER_SET_ONE = List.of(
        Map.of("answer", "A loop that amplifies change", "isCorrect", true),
        Map.of("answer", "A loop that balances change", "isCorrect", false),
        Map.of("answer", "A fixed delay", "isCorrect", false),
        Map.of("answer", "A single stock", "isCorrect", false)
    );

    private static final List<Map<String, Object>> ANSWER_SET_TWO = List.of(
        Map.of("answer", "Reinforces its own growth", "isCorrect", false),
        Map.of("answer", "Dampens its own change", "isCorrect", true),
        Map.of("answer", "A constant value", "isCorrect", false),
        Map.of("answer", "A one-off event", "isCorrect", false)
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void concurrentAnswerReplacementsAreAppliedOneAfterTheOther() throws Exception {
        TestUser user = register("Kenneth", "kenneth@example.com");
        Long quizId = createQuiz(user.id(), "quizqcc01");
        Long questionId = createQuestion(quizId, "questncc01", "What is a reinforcing loop?");
        createAnswer(questionId, "answcc0001", "Original A", true, 0);
        createAnswer(questionId, "answcc0002", "Original B", false, 1);
        createAnswer(questionId, "answcc0003", "Original C", false, 2);
        createAnswer(questionId, "answcc0004", "Original D", false, 3);

        List<Integer> statuses = replaceAnswersConcurrently(
            user,
            "quizqcc01",
            "questncc01",
            List.of(ANSWER_SET_ONE, ANSWER_SET_TWO)
        );

        assertEquals(List.of(200, 200), statuses.stream().sorted().toList());

        List<TestAnswer> finalAnswers = answers(questionId);

        assertEquals(4, finalAnswers.size());
        assertEquals(1, finalAnswers.stream().filter(TestAnswer::correct).count());
        assertTrue(
            finalAnswers.equals(expectedAnswers(ANSWER_SET_ONE)) || finalAnswers.equals(expectedAnswers(ANSWER_SET_TWO)),
            "final answers must be exactly one submitted replacement set, was " + finalAnswers
        );
    }

    private List<Integer> replaceAnswersConcurrently(
        TestUser user,
        String quizId,
        String questionId,
        List<List<Map<String, Object>>> answerSets
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(answerSets.size());
        CyclicBarrier barrier = new CyclicBarrier(answerSets.size());
        List<Future<Integer>> futures = new ArrayList<>();

        for (List<Map<String, Object>> answerSet : answerSets) {
            futures.add(executor.submit(() -> {
                barrier.await();

                MvcResult result = mockMvc.perform(patch(QUESTION_ENDPOINT, quizId, questionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answers", answerSet))))
                    .andReturn();

                return result.getResponse().getStatus();
            }));
        }

        List<Integer> statuses = new ArrayList<>();

        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }

        executor.shutdown();

        return statuses;
    }

    private List<TestAnswer> expectedAnswers(List<Map<String, Object>> answerSet) {
        return answerSet
            .stream()
            .map(a -> new TestAnswer((String) a.get("answer"), (Boolean) a.get("isCorrect")))
            .toList();
    }

    private List<TestAnswer> answers(Long questionId) {
        return jdbcTemplate.query(
            """
            SELECT answer_text, is_correct
            FROM quiz_answer
            WHERE question_id = ?
            ORDER BY position ASC
            """,
            (rs, rowNum) -> new TestAnswer(rs.getString("answer_text"), rs.getBoolean("is_correct")),
            questionId
        );
    }

    private TestUser register(String fullName, String email) throws Exception {
        String accessToken = registerAndAuthenticate(fullName, email, VALID_PASSWORD);
        Long userId = Long.valueOf(jwtDecoder.decode(accessToken).getSubject());

        return new TestUser(userId, accessToken);
    }

    private Long createQuiz(Long userId, String publicId) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO quiz (user_id, public_id, title, description, source_type)
            VALUES (?, ?, 'Systems quiz', 'Checks systems thinking.', 'MANUAL')
            RETURNING id
            """,
            Long.class,
            userId,
            publicId
        );
    }

    private Long createQuestion(Long quizId, String publicId, String questionText) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO quiz_question (quiz_id, public_id, question_text, question_type, position)
            VALUES (?, ?, ?, 'MULTIPLE_CHOICE', 0)
            RETURNING id
            """,
            Long.class,
            quizId,
            publicId,
            questionText
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

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record TestUser(
        Long id,
        String accessToken
    ) {}

    private record TestAnswer(
        String text,
        boolean correct
    ) {}
}

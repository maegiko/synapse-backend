package com.synapse.backend.shared.ratelimit;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"ratelimit.ai.limit=10", "ratelimit.ai-daily.limit=2"})
class AiDailyRateLimitIntegrationTest extends PostgresIntegrationTest {

    private static final String SUMMARY_ENDPOINT = "/api/notes/summarise";
    private static final String VALID_PASSWORD = "password123";

    private static final int AI_REQUESTS_PER_DAY = 2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private LLMClient llmClient;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void summariseNotesReturnsTooManyRequestsAfterDailyAiLimit() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn(validSummaryJson());
        String accessToken = registerAndGetAccessToken("Kenneth", "kenneth@example.com");

        for (int i = 0; i < AI_REQUESTS_PER_DAY; i++) {
            summarise(accessToken).andExpect(status().isOk());
        }

        summarise(accessToken)
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.message").value(startsWith("Too many requests.")));
    }

    @Test
    void dailyAiLimitAppliesPerUser() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn(validSummaryJson());
        String accessToken = registerAndGetAccessToken("Kenneth", "kenneth@example.com");
        String otherAccessToken = registerAndGetAccessToken("Ada", "ada@example.com");

        for (int i = 0; i < AI_REQUESTS_PER_DAY; i++) {
            summarise(accessToken).andExpect(status().isOk());
        }

        summarise(accessToken).andExpect(status().isTooManyRequests());
        summarise(otherAccessToken).andExpect(status().isOk());
    }

    private ResultActions summarise(String accessToken) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "notes.txt",
            "text/plain",
            "Behavioural modelling lecture notes".getBytes(StandardCharsets.UTF_8)
        );

        return mockMvc.perform(multipart(SUMMARY_ENDPOINT)
            .file(file)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private String registerAndGetAccessToken(String fullName, String email) throws Exception {
        return registerAndAuthenticate(fullName, email, VALID_PASSWORD);
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

}

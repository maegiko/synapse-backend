package com.synapse.backend.shared.ratelimit;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.auth.dto.LoginRequest;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "ratelimit.api.limit=5")
class RateLimitIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String LOGIN_ENDPOINT = "/api/auth/login";
    private static final String SUMMARY_ENDPOINT = "/api/notes/summarise";
    private static final String NOTES_LIST_ENDPOINT = "/api/notes/list";
    private static final String VALID_PASSWORD = "password123";
    private static final String CONTEXT_PATH = "/synapse";

    private static final int AI_REQUESTS_PER_MINUTE = 3;
    private static final int API_REQUESTS_PER_MINUTE = 5;
    private static final int LOGIN_ATTEMPTS = 10;
    private static final int REGISTRATIONS_PER_HOUR = 3;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private LLMClient llmClient;

    @BeforeEach
    void deleteUsers() {
        jdbcTemplate.execute("DELETE FROM app_user");
    }

    @Test
    void summariseNotesReturnsTooManyRequestsAfterAiLimit() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn(validSummaryJson());
        String accessToken = registerAndGetAccessToken("Kenneth", "kenneth@example.com");

        for (int i = 0; i < AI_REQUESTS_PER_MINUTE; i++) {
            summarise(accessToken).andExpect(status().isOk());
        }

        summarise(accessToken)
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.message").value(startsWith("Too many requests.")));
    }

    @Test
    void aiLimitAppliesPerUser() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn(validSummaryJson());
        String accessToken = registerAndGetAccessToken("Kenneth", "kenneth@example.com");
        String otherAccessToken = registerAndGetAccessToken("Ada", "ada@example.com");

        for (int i = 0; i < AI_REQUESTS_PER_MINUTE; i++) {
            summarise(accessToken).andExpect(status().isOk());
        }

        summarise(accessToken).andExpect(status().isTooManyRequests());
        summarise(otherAccessToken).andExpect(status().isOk());
    }

    @Test
    void authenticatedApiRequestsReturnTooManyRequestsAfterApiLimit() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", "kenneth@example.com");

        for (int i = 0; i < API_REQUESTS_PER_MINUTE; i++) {
            mockMvc.perform(get(NOTES_LIST_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        }

        mockMvc.perform(get(NOTES_LIST_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.message").value(startsWith("Too many requests.")));
    }

    @Test
    void registerReturnsTooManyRequestsAfterRegistrationLimit() throws Exception {
        for (int i = 0; i < REGISTRATIONS_PER_HOUR; i++) {
            register("Kenneth", "kenneth" + i + "@example.com").andExpect(status().isCreated());
        }

        register("Kenneth", "kenneth-blocked@example.com")
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.message").value(startsWith("Too many requests.")));
    }

    @Test
    void loginReturnsTooManyRequestsAfterAttemptLimit() throws Exception {
        registerAndGetAccessToken("Kenneth", "kenneth@example.com");

        for (int i = 0; i < LOGIN_ATTEMPTS; i++) {
            login("kenneth@example.com", "wrong-password").andExpect(status().isUnauthorized());
        }

        login("kenneth@example.com", VALID_PASSWORD)
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.message").value(startsWith("Too many requests.")));
    }

    @Test
    void loginLimitAppliesPerEmailAcrossAddresses() throws Exception {
        registerAndGetAccessToken("Kenneth", "kenneth@example.com");
        registerAndGetAccessToken("Ada", "ada@example.com");

        for (int i = 0; i < LOGIN_ATTEMPTS; i++) {
            login("kenneth@example.com", "wrong-password", "10.0.0.1").andExpect(status().isUnauthorized());
        }

        login("kenneth@example.com", VALID_PASSWORD, "10.0.0.2").andExpect(status().isTooManyRequests());
        login("ada@example.com", VALID_PASSWORD, "10.0.0.2").andExpect(status().isOk());
    }

    @Test
    void loginLimitAppliesPerAddressAcrossEmails() throws Exception {
        registerAndGetAccessToken("Kenneth", "kenneth@example.com");

        for (int i = 0; i < LOGIN_ATTEMPTS; i++) {
            login("unknown" + i + "@example.com", VALID_PASSWORD, "10.0.0.3").andExpect(status().isUnauthorized());
        }

        login("kenneth@example.com", VALID_PASSWORD, "10.0.0.3").andExpect(status().isTooManyRequests());
        login("kenneth@example.com", VALID_PASSWORD, "10.0.0.4").andExpect(status().isOk());
    }

    @Test
    void getRequestOnAiPathUsesApiLimit() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", "kenneth@example.com");

        for (int i = 0; i < AI_REQUESTS_PER_MINUTE + 1; i++) {
            mockMvc.perform(get("/api/flashcards/generate").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
        }
    }

    @Test
    void aiLimitAppliesUnderContextPath() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn(validSummaryJson());
        String accessToken = registerAndGetAccessToken("Kenneth", "kenneth@example.com");

        for (int i = 0; i < AI_REQUESTS_PER_MINUTE; i++) {
            summariseUnderContextPath(accessToken).andExpect(status().isOk());
        }

        summariseUnderContextPath(accessToken).andExpect(status().isTooManyRequests());
    }

    @Test
    void preflightRequestsAreNotLimitedAndIncludeCorsHeaders() throws Exception {
        for (int i = 0; i < API_REQUESTS_PER_MINUTE + 2; i++) {
            mockMvc.perform(options(NOTES_LIST_ENDPOINT)
                    .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        }
    }

    private ResultActions summarise(String accessToken) throws Exception {
        return mockMvc.perform(multipart(SUMMARY_ENDPOINT)
            .file(textFile())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private MockMultipartFile textFile() {
        return new MockMultipartFile(
            "file",
            "notes.txt",
            "text/plain",
            "Behavioural modelling lecture notes".getBytes(StandardCharsets.UTF_8)
        );
    }

    private ResultActions summariseUnderContextPath(String accessToken) throws Exception {
        return mockMvc.perform(multipart(CONTEXT_PATH + SUMMARY_ENDPOINT)
            .file(textFile())
            .contextPath(CONTEXT_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private ResultActions register(String fullName, String email) throws Exception {
        RegisterRequest request = new RegisterRequest(fullName, email, VALID_PASSWORD);

        return mockMvc.perform(post(REGISTER_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return login(email, password, "127.0.0.1");
    }

    private ResultActions login(String email, String password, String clientIp) throws Exception {
        LoginRequest request = new LoginRequest(email, password);

        return mockMvc.perform(post(LOGIN_ENDPOINT)
            .with(fromAddress(clientIp))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
    }

    private RequestPostProcessor fromAddress(String clientIp) {
        return request -> {
            request.setRemoteAddr(clientIp);

            return request;
        };
    }

    private String registerAndGetAccessToken(String fullName, String email) throws Exception {
        MvcResult result = register(fullName, email)
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asString();
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

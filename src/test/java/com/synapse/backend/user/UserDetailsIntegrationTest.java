package com.synapse.backend.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.flashcards.dto.review.ReviewDeckRequest;
import com.synapse.backend.flashcards.enums.ReviewRating;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class UserDetailsIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String USER_ME_ENDPOINT = "/api/user/details";
    private static final String DECK_REVIEW_ENDPOINT = "/api/flashcards/{deckId}/review";
    private static final String VALID_PASSWORD = "password123";

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
    void getUserDetailsReturnsCurrentUserWhenAuthenticated() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", "kenneth@example.com");

        mockMvc.perform(get(USER_ME_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Kenneth"))
            .andExpect(jsonPath("$.email").value("kenneth@example.com"))
            .andExpect(jsonPath("$.totalFlashcardsReviewed").value(0));
    }

    @Test
    void getUserDetailsReportsLifetimeFlashcardsReviewed() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", "kenneth@example.com");
        Long userId = Long.valueOf(jwtDecoder.decode(accessToken).getSubject());
        createDeckWithCards(userId, "deckdetail", 3);

        mockMvc.perform(post(DECK_REVIEW_ENDPOINT, "deckdetail")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReviewDeckRequest(ReviewRating.GOOD)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk());

        mockMvc.perform(get(USER_ME_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalFlashcardsReviewed").value(3));
    }

    @Test
    void getUserDetailsReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get(USER_ME_ENDPOINT))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserDetailsReturnsUnauthorizedWhenTokenIsInvalid() throws Exception {
        mockMvc.perform(get(USER_ME_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }

    private String registerAndGetAccessToken(String fullName, String email) throws Exception {
        RegisterRequest request = new RegisterRequest(fullName, email, VALID_PASSWORD);

        MvcResult result = mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asString();
    }

    private void createDeckWithCards(Long userId, String publicId, int numberOfCards) {
        Long deckId = jdbcTemplate.queryForObject(
            """
            INSERT INTO flashcard_deck (user_id, title, source_type, public_id)
            VALUES (?, 'Systems deck', 'NOTE', ?)
            RETURNING id
            """,
            Long.class,
            userId,
            publicId
        );

        for (int position = 0; position < numberOfCards; position++) {
            jdbcTemplate.update(
                """
                INSERT INTO flashcard (deck_id, question, answer, position, public_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                deckId,
                "Question " + position,
                "Answer " + position,
                position,
                publicId.substring(4) + "c" + position
            );
        }
    }
}

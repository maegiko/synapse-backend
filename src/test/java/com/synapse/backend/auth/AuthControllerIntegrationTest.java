package com.synapse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
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
    void registerCreatesUserAndReturnsAccessToken() throws Exception {
        RegisterRequest request = new RegisterRequest("Kenneth", "kenneth@example.com", VALID_PASSWORD);

        MvcResult result = mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fullName").value("Kenneth"))
            .andExpect(jsonPath("$.email").value("kenneth@example.com"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

        Map<String, Object> savedUser = jdbcTemplate.queryForMap(
            "SELECT id, full_name, email, password_hash FROM app_user WHERE email = ?",
            "kenneth@example.com"
        );
        String accessToken = objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asString();
        Jwt jwt = jwtDecoder.decode(accessToken);

        assertThat(savedUser.get("full_name")).isEqualTo("Kenneth");
        assertThat(savedUser.get("email")).isEqualTo("kenneth@example.com");
        assertThat(savedUser.get("password_hash")).isNotEqualTo(VALID_PASSWORD);
        assertThat(savedUser.get("password_hash").toString()).startsWith("$2");
        assertThat(jwt.getSubject()).isEqualTo(savedUser.get("id").toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("kenneth@example.com");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Kenneth");
    }

    @Test
    void registerReturnsConflictWhenEmailAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest("Kenneth", "kenneth@example.com", VALID_PASSWORD);

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Email is already registered: kenneth@example.com"));
    }

    @Test
    void registerReturnsBadRequestWhenNameIsMissing() throws Exception {
        RegisterRequest request = new RegisterRequest(null, "kenneth@example.com", VALID_PASSWORD);

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("fullName: must not be blank"));
    }

    @Test
    void registerReturnsBadRequestWhenEmailIsInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest("Kenneth", "not-an-email", VALID_PASSWORD);

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("email: must be a well-formed email address"));
    }

    @Test
    void registerReturnsBadRequestWhenPasswordIsTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest("Kenneth", "kenneth@example.com", "short");

        mockMvc.perform(post(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("password: size must be between 8 and 64"));
    }
}

package com.synapse.backend.notes;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.support.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class NotesSummaryIntegrationTest extends PostgresIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String SUMMARY_ENDPOINT = "/api/notes/summarise";
    private static final String VALID_PASSWORD = "password123";

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
    void summariseNotesReturnsStructuredSummaryForPdf() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn(validSummaryJson());
        String accessToken = registerAndGetAccessToken("Kenneth", "kenneth@example.com");
        MockMultipartFile file = pdfFile("notes.pdf", "Behavioural modelling lecture notes");

        MvcResult result = mockMvc.perform(multipart(SUMMARY_ENDPOINT)
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isString())
            .andExpect(jsonPath("$.title").value("Behavioural Modelling"))
            .andExpect(jsonPath("$.overview").value("A short overview."))
            .andExpect(jsonPath("$.keypoints[0]").value("Models simplify behaviour."))
            .andExpect(jsonPath("$.concepts[0].name").value("Model"))
            .andExpect(jsonPath("$.concepts[0].explanation").value("A simplified representation."))
            .andExpect(jsonPath("$.importantTerms[0]").value("behaviour"))
            .andReturn();

        String noteId = objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("id")
            .asString();

        UUID.fromString(noteId);
        Integer savedNoteCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM note WHERE public_id = ?::uuid",
            Integer.class,
            noteId
        );

        org.assertj.core.api.Assertions.assertThat(savedNoteCount).isEqualTo(1);
    }

    @Test
    void summariseNotesReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        MockMultipartFile file = pdfFile("notes.pdf", "Behavioural modelling lecture notes");

        mockMvc.perform(multipart(SUMMARY_ENDPOINT).file(file))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void summariseNotesReturnsBadRequestWhenFileIsEmpty() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", "kenneth@example.com");
        MockMultipartFile file = new MockMultipartFile("file", "notes.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart(SUMMARY_ENDPOINT)
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("The file cannot be empty."));
    }

    @Test
    void summariseNotesReturnsBadRequestWhenFileTypeIsUnsupported() throws Exception {
        String accessToken = registerAndGetAccessToken("Kenneth", "kenneth@example.com");
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "notes".getBytes());

        mockMvc.perform(multipart(SUMMARY_ENDPOINT)
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("The filetype text/plain is not supported."));
    }

    @Test
    void summariseNotesReturnsBadGatewayWhenLlmReturnsInvalidJson() throws Exception {
        when(llmClient.generate(any(LLMRequest.class))).thenReturn("not json");
        String accessToken = registerAndGetAccessToken("Kenneth", "kenneth@example.com");
        MockMultipartFile file = pdfFile("notes.pdf", "Behavioural modelling lecture notes");

        mockMvc.perform(multipart(SUMMARY_ENDPOINT)
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.message").value("Failed to parse LLM response"));
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

    private MockMultipartFile pdfFile(String filename, String text) throws IOException {
        return new MockMultipartFile("file", filename, "application/pdf", pdfContaining(text));
    }

    private byte[] pdfContaining(String text) throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText(text);
                contentStream.endText();
            }

            document.save(output);
            return output.toByteArray();
        }
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

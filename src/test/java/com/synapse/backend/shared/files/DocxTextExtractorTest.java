package com.synapse.backend.shared.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.synapse.backend.shared.files.exceptions.FileParsingException;

class DocxTextExtractorTest {

    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DocxTextExtractor extractor = new DocxTextExtractor();

    @Test
    void supportsDocxContentType() {
        assertThat(extractor.supports(DOCX_TYPE)).isTrue();
    }

    @Test
    void doesNotSupportOtherContentTypes() {
        assertThat(extractor.supports("application/pdf")).isFalse();
        assertThat(extractor.supports("application/msword")).isFalse();
        assertThat(extractor.supports(null)).isFalse();
    }

    @Test
    void extractTextReturnsDocxText() throws IOException {
        MultipartFile file = new MockMultipartFile(
            "file",
            "notes.docx",
            DOCX_TYPE,
            docxContaining("Quarterly planning notes")
        );

        String text = extractor.extractText(file);

        assertThat(text).contains("Quarterly planning notes");
    }

    @Test
    void extractTextThrowsFileParsingExceptionForInvalidDocx() {
        MultipartFile file = new MockMultipartFile(
            "file",
            "broken.docx",
            DOCX_TYPE,
            "not a docx".getBytes()
        );

        assertThatThrownBy(() -> extractor.extractText(file))
            .isInstanceOf(FileParsingException.class)
            .hasMessage("Failed to parse DOCX");
    }

    private byte[] docxContaining(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);

            document.write(output);
            return output.toByteArray();
        }
    }

}

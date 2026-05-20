package com.synapse.backend.shared.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.synapse.backend.shared.files.exceptions.FileParsingException;

class PDFTextExtractorTest {

    private final PDFTextExtractor extractor = new PDFTextExtractor();

    @Test
    void supportsPdfContentType() {
        assertThat(extractor.supports("application/pdf")).isTrue();
    }

    @Test
    void doesNotSupportOtherContentTypes() {
        assertThat(extractor.supports("text/plain")).isFalse();
        assertThat(extractor.supports(null)).isFalse();
    }

    @Test
    void extractTextReturnsPdfText() throws IOException {
        MultipartFile file = new MockMultipartFile(
            "file",
            "notes.pdf",
            "application/pdf",
            pdfContaining("Quarterly planning notes")
        );

        String text = extractor.extractText(file);

        assertThat(text).contains("Quarterly planning notes");
    }

    @Test
    void extractTextThrowsFileParsingExceptionForInvalidPdf() {
        MultipartFile file = new MockMultipartFile(
            "file",
            "broken.pdf",
            "application/pdf",
            "not a pdf".getBytes()
        );

        assertThatThrownBy(() -> extractor.extractText(file))
            .isInstanceOf(FileParsingException.class)
            .hasMessage("Failed to parse PDF");
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

}

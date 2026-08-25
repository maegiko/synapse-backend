package com.synapse.backend.shared.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.synapse.backend.shared.files.exceptions.FileParsingException;

class PlainTextExtractorTest {

    private final PlainTextExtractor extractor = new PlainTextExtractor();

    @Test
    void supportsPlainTextContentType() {
        assertThat(extractor.supports("text/plain")).isTrue();
    }

    @Test
    void supportsParameterisedPlainTextContentType() {
        assertThat(extractor.supports("text/plain;charset=UTF-8")).isTrue();
        assertThat(extractor.supports("text/plain; charset=ISO-8859-1")).isTrue();
    }

    @Test
    void doesNotSupportOtherContentTypes() {
        assertThat(extractor.supports("application/pdf")).isFalse();
        assertThat(extractor.supports(null)).isFalse();
    }

    @Test
    void doesNotSupportMalformedContentTypes() {
        assertThat(extractor.supports("text/")).isFalse();
        assertThat(extractor.supports("text plain")).isFalse();
        assertThat(extractor.supports("text/plain;charset=not a charset")).isFalse();
        assertThat(extractor.supports("notacontenttype")).isFalse();
    }

    @Test
    void extractTextReturnsFileText() {
        MultipartFile file = textFile("text/plain", "Quarterly planning notes", StandardCharsets.UTF_8);

        String text = extractor.extractText(file);

        assertThat(text).isEqualTo("Quarterly planning notes");
    }

    @Test
    void extractTextReadsParameterisedUtf8ContentType() {
        MultipartFile file = textFile("text/plain;charset=UTF-8", "Café résumé — 日本語", StandardCharsets.UTF_8);

        String text = extractor.extractText(file);

        assertThat(text).isEqualTo("Café résumé — 日本語");
    }

    @Test
    void extractTextReadsDeclaredNonUtf8Charset() {
        MultipartFile file = textFile("text/plain;charset=ISO-8859-1", "Café résumé", StandardCharsets.ISO_8859_1);

        String text = extractor.extractText(file);

        assertThat(text).isEqualTo("Café résumé");
    }

    @Test
    void extractTextDefaultsToUtf8WhenNoCharsetIsDeclared() {
        MultipartFile file = textFile("text/plain", "Café résumé — 日本語", StandardCharsets.UTF_8);

        String text = extractor.extractText(file);

        assertThat(text).isEqualTo("Café résumé — 日本語");
    }

    @Test
    void extractTextThrowsFileParsingExceptionForMalformedContentType() {
        MultipartFile file = textFile("text/plain;charset=not a charset", "Quarterly planning notes", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extractText(file))
            .isInstanceOf(FileParsingException.class)
            .hasMessage("Failed to parse TXT");
    }

    @Test
    void extractTextThrowsFileParsingExceptionForUnsupportedCharset() {
        MultipartFile file = textFile("text/plain;charset=made-up-charset", "Quarterly planning notes", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extractText(file))
            .isInstanceOf(FileParsingException.class)
            .hasMessage("Failed to parse TXT");
    }

    @Test
    void extractTextThrowsFileParsingExceptionForInvalidEncodedText() {
        MultipartFile file = new MockMultipartFile(
            "file",
            "broken.txt",
            "text/plain;charset=UTF-8",
            new byte[] {(byte) 0xC3, (byte) 0x28}
        );

        assertThatThrownBy(() -> extractor.extractText(file))
            .isInstanceOf(FileParsingException.class)
            .hasMessage("Failed to parse TXT");
    }

    private MultipartFile textFile(String contentType, String text, Charset charset) {
        return new MockMultipartFile("file", "notes.txt", contentType, text.getBytes(charset));
    }

}

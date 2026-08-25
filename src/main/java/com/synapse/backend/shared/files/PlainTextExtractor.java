package com.synapse.backend.shared.files;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.synapse.backend.shared.files.exceptions.FileParsingException;

@Component
public class PlainTextExtractor implements FileTextExtractor {

    @Override
    public boolean supports(String fileType) {
        if (fileType == null) return false;

        try {
            return MediaType.TEXT_PLAIN.equalsTypeAndSubtype(MediaType.parseMediaType(fileType));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }

    @Override
    public String extractText(MultipartFile file) {
        try {
            Charset charset = charsetOf(file.getContentType());
            return charset.newDecoder().decode(ByteBuffer.wrap(file.getBytes())).toString();
        } catch (InvalidMediaTypeException | IOException e) {
            throw new FileParsingException("TXT");
        }
    }

    /**
     * Returns the charset declared by a content type, defaulting to UTF-8.
     *
     * @param fileType the content type of the file.
     * @return the declared charset, or UTF-8 if none is declared.
     * @throws InvalidMediaTypeException if the content type or its charset is invalid.
     */
    private Charset charsetOf(String fileType) {
        Charset charset = MediaType.parseMediaType(fileType).getCharset();

        return charset != null ? charset : StandardCharsets.UTF_8;
    }

}

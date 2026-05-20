package com.synapse.backend.shared.files;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.synapse.backend.shared.files.exceptions.UnsupportedFileException;

@Service
public class FileParsingService {
    private final List<FileTextExtractor> textExtractors;

    public FileParsingService(List<FileTextExtractor> textExtractors) {
        this.textExtractors = textExtractors;
    }

    /**
     * Extracts text from a supported file.
     * @param file the file to extract text from.
     * @return the text extracted from the file.
     * @throws UnsupportedFileException if the file type is not supported.
     * @throws FileParsingException if file cannot be parsed.
     */
    public String extractText(MultipartFile file) {
        String fileType = file.getContentType();

        for (FileTextExtractor f : textExtractors) {
            if (f.supports(fileType)) {
                return f.extractText(file);
            }
        }

        throw new UnsupportedFileException(fileType);
    }

}

package com.synapse.backend.shared.files;

import org.springframework.web.multipart.MultipartFile;

public interface FileTextExtractor {

    /**
     * Returns whether the extractor supports a given file type.
     * @param fileType the file type to be parsed.
     * @return boolean
     */
    public boolean supports(String fileType);

    /**
     * Extract text from a file.
     * @param file the file to extract text from.
     * @return the text of the file.
     * @throws FileParsingException if file cannot be parsed.
     */
    public String extractText(MultipartFile file);

}

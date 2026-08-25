package com.synapse.backend.shared.files;

import java.io.IOException;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.synapse.backend.shared.files.exceptions.FileParsingException;

@Component
public class DocxTextExtractor implements FileTextExtractor {
    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Override
    public boolean supports(String fileType) {
        if (fileType == null) return false;

        return fileType.equals(DOCX_TYPE);
    }

    @Override
    public String extractText(MultipartFile file) {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream());
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException | RuntimeException e) {
            // POI reports unreadable or non-OOXML files with unchecked exceptions.
            throw new FileParsingException("DOCX");
        }
    }

}

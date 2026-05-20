package com.synapse.backend.shared.files;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.synapse.backend.shared.files.exceptions.FileParsingException;

@Component
public class PDFTextExtractor implements FileTextExtractor {

    @Override
    public boolean supports(String fileType) {
        if (fileType == null) return false;

        return fileType.equals("application/pdf");
    }

    @Override
    public String extractText(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            throw new FileParsingException("PDF");
        }
    }

}

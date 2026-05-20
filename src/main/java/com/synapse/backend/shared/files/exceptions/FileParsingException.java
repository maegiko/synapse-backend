package com.synapse.backend.shared.files.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class FileParsingException extends BadRequestException {

    public FileParsingException(String fileType) {
        super(String.format("Failed to parse %s", fileType));
    }

}

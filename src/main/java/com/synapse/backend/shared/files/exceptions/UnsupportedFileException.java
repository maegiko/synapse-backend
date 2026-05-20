package com.synapse.backend.shared.files.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class UnsupportedFileException extends BadRequestException {

    public UnsupportedFileException(String fileType) {
        super(String.format("The filetype %s is not supported.", fileType));
    }

}

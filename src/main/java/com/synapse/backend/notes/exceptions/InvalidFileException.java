package com.synapse.backend.notes.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class InvalidFileException extends BadRequestException {

    public InvalidFileException(String message) {
        super(message);
    }

}

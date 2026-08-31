package com.synapse.backend.notes.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class InvalidNoteDetailsException extends BadRequestException {

    public InvalidNoteDetailsException(String message) {
        super(message);
    }

}

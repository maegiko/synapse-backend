package com.synapse.backend.groups.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class InvalidGroupDetailsException extends BadRequestException {

    public InvalidGroupDetailsException(String message) {
        super(message);
    }

}

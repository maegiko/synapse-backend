package com.synapse.backend.user.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class InvalidUserDetailsException extends BadRequestException {

    public InvalidUserDetailsException(String message) {
        super(message);
    }

}

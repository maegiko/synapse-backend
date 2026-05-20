package com.synapse.backend.shared.exceptions;

public abstract class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

}

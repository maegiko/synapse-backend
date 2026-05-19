package com.synapse.backend.shared.exceptions;

public abstract class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

}

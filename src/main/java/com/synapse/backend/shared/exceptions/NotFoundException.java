package com.synapse.backend.shared.exceptions;

public abstract class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

}

package com.synapse.backend.shared.exceptions;

public abstract class UnauthorisedException extends RuntimeException {

    public UnauthorisedException(String message) {
        super(message);
    }

}

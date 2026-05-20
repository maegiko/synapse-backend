package com.synapse.backend.shared.exceptions;

public abstract class BadGatewayException extends RuntimeException {

    public BadGatewayException(String message) {
        super(message);
    }

}

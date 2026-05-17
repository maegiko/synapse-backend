package com.synapse.backend.auth.exceptions;

public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("Email is already registered: " + email);
    }
}

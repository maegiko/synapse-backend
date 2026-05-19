package com.synapse.backend.auth.exceptions;

import com.synapse.backend.shared.exceptions.ConflictException;

public class EmailAlreadyExistsException extends ConflictException {

    public EmailAlreadyExistsException(String email) {
        super("Email is already registered: " + email);
    }
}

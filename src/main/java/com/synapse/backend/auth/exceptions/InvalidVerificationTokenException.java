package com.synapse.backend.auth.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class InvalidVerificationTokenException extends BadRequestException {

    public InvalidVerificationTokenException() {
        super("Invalid or expired verification token.");
    }
}

package com.synapse.backend.auth.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class InvalidPasswordResetTokenException extends BadRequestException {

    public InvalidPasswordResetTokenException() {
        super("Invalid or expired password reset token.");
    }
}

package com.synapse.backend.auth.exceptions;

import com.synapse.backend.shared.exceptions.UnauthorisedException;

public class InvalidRefreshTokenException extends UnauthorisedException {

    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token.");
    }
}

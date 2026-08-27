package com.synapse.backend.auth.exceptions;

import com.synapse.backend.shared.exceptions.UnauthorisedException;

public class IncorrectPasswordException extends UnauthorisedException {

    public IncorrectPasswordException() {
        super("Current password is incorrect.");
    }
}

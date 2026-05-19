package com.synapse.backend.auth.exceptions;

import com.synapse.backend.shared.exceptions.UnauthorisedException;

public class LoginFailException extends UnauthorisedException {

    public LoginFailException() {
        super("Invalid email or password.");
    }
}

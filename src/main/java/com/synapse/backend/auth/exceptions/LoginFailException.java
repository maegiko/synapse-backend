package com.synapse.backend.auth.exceptions;

public class LoginFailException extends RuntimeException {

    public LoginFailException() {
        super("Invalid email or password.");
    }
}

package com.synapse.backend.auth.exceptions;

import com.synapse.backend.shared.exceptions.UnauthorisedException;

public class EmailNotVerifiedException extends UnauthorisedException {

    public EmailNotVerifiedException() {
        super("Email address is not verified. Check your inbox for the verification link.");
    }
}

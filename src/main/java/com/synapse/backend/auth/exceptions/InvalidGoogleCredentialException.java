package com.synapse.backend.auth.exceptions;

import com.synapse.backend.shared.exceptions.UnauthorisedException;

/**
 * One generic failure for every way a Google credential can be unacceptable: a bad
 * signature, the wrong issuer, an audience that is not this application, an expired token,
 * a missing subject or unverified email, and a missing, mismatched, or already used nonce.
 * They share a message on purpose, so a caller probing the endpoint learns nothing about
 * which check it failed.
 */
public class InvalidGoogleCredentialException extends UnauthorisedException {

    public InvalidGoogleCredentialException() {
        super("Google sign-in could not be verified. Try again.");
    }
}

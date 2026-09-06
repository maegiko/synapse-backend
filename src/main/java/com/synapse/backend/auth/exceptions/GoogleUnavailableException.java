package com.synapse.backend.auth.exceptions;

import com.synapse.backend.shared.exceptions.BadGatewayException;

/**
 * Google itself could not be reached to fetch the keys a token is verified against. Kept
 * apart from {@link InvalidGoogleCredentialException} because the credential may well be
 * fine and retrying is the right advice, and because the verifier reports this case
 * distinctly rather than as a failed check.
 */
public class GoogleUnavailableException extends BadGatewayException {

    public GoogleUnavailableException() {
        super("Google sign-in is temporarily unavailable. Try again shortly.");
    }
}

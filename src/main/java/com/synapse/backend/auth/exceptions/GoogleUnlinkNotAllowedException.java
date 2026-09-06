package com.synapse.backend.auth.exceptions;

import com.synapse.backend.shared.exceptions.ConflictException;

/**
 * Unlinking Google would leave the account with no way to sign in, because it has no
 * password. The user has to set one through the password reset flow first.
 */
public class GoogleUnlinkNotAllowedException extends ConflictException {

    public GoogleUnlinkNotAllowedException() {
        super("This account signs in with Google only. Set a password before unlinking Google.");
    }
}

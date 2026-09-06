package com.synapse.backend.auth.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

/**
 * The Google Account signs in with an address Google does not own: a third-party address
 * with no {@code hd} claim. Google has verified that the person can read that inbox, but
 * not that the address is theirs to keep, so it cannot be used to create or claim a Synapse
 * account. The message says what to do instead and describes no account.
 */
public class GoogleEmailNotAuthoritativeException extends BadRequestException {

    public GoogleEmailNotAuthoritativeException() {
        super("Google does not verify ownership of this email address. Register with Synapse, confirm your "
            + "address, then link Google from your account settings.");
    }
}

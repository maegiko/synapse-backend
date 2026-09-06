package com.synapse.backend.auth.exceptions;

import com.synapse.backend.shared.exceptions.ConflictException;

/** A Google identity cannot be attached because another one already holds the place. */
public class GoogleAccountConflictException extends ConflictException {

    public GoogleAccountConflictException(String message) {
        super(message);
    }
}

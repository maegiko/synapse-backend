package com.synapse.backend.auth.exceptions;

import com.synapse.backend.shared.exceptions.UnauthorisedException;

/**
 * An authenticated route asked for the current password of an account that has never had
 * one, which is what a Google-only account looks like. Reported instead of comparing the
 * supplied password against a null hash, so the caller is told how to get a password rather
 * than being told theirs is wrong.
 */
public class PasswordNotSetException extends UnauthorisedException {

    public PasswordNotSetException() {
        super("This account has no password. Use the forgotten-password flow to set one.");
    }
}

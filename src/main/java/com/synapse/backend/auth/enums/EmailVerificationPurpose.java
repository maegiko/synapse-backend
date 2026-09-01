package com.synapse.backend.auth.enums;

/** Why a verification token was issued, which decides what consuming it does. */
public enum EmailVerificationPurpose {
    REGISTRATION,
    EMAIL_CHANGE
}

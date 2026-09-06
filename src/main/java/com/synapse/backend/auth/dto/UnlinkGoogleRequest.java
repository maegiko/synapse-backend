package com.synapse.backend.auth.dto;

import com.synapse.backend.shared.validation.Password;

/**
 * The account's current password, which is what authorises removing a way into the account
 * and what proves the account still has another one.
 */
public record UnlinkGoogleRequest(
    @Password
    String currentPassword
) {}

package com.synapse.backend.auth.dto;

import com.synapse.backend.user.User;

/**
 * The account a Google credential resolved to, and the refresh token issued to it.
 *
 * <p>Internal to the sign-in workflow: the two are produced in one transaction, so
 * an account that was created or claimed and a session that was minted for it
 * commit together or not at all.</p>
 */
public record GoogleSignInResult(
    User user,
    String refreshToken
) {}

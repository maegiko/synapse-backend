package com.synapse.backend.user.dto;

/**
 * The signed-in user's profile.
 *
 * <p>{@code hasPassword} and {@code googleLinked} are how the account settings screen knows
 * which ways in this account has, and so whether to offer linking, unlinking, or setting a
 * first password. At least one of them is always true; the database refuses a row where
 * neither is. The Google Account's own address is deliberately not here: it is not the
 * address Synapse uses for anything, and it can differ from {@code email}.</p>
 */
public record UserDetailsResponse(
    String fullName,
    String email,
    long totalFlashcardsReviewed,
    String timeZone,
    boolean hasPassword,
    boolean googleLinked
) {}

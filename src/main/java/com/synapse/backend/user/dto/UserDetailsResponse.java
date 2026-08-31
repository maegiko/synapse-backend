package com.synapse.backend.user.dto;

public record UserDetailsResponse(
    String fullName,
    String email,
    long totalFlashcardsReviewed,
    String timeZone
) {}

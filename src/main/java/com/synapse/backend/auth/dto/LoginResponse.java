package com.synapse.backend.auth.dto;

public record LoginResponse(
    String fullName,
    String email,
    String accessToken
) {}

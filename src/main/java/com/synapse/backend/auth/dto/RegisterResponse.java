package com.synapse.backend.auth.dto;

public record RegisterResponse(
    String fullName,
    String email,
    String accessToken
) {}

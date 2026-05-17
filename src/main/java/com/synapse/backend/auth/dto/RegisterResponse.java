package com.synapse.backend.auth.dto;

public record RegisterResponse(
    String name,
    String email,
    String accessToken
) {}

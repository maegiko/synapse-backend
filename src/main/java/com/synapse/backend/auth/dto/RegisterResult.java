package com.synapse.backend.auth.dto;

public record RegisterResult(
    RegisterResponse response,
    String refreshToken
) {}

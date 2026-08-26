package com.synapse.backend.auth.dto;

public record LoginResult(
    LoginResponse response,
    String refreshToken
) {}

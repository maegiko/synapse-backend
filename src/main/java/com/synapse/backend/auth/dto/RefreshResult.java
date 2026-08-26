package com.synapse.backend.auth.dto;

public record RefreshResult(
    RefreshResponse response,
    String refreshToken
) {}

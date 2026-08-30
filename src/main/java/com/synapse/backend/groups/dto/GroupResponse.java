package com.synapse.backend.groups.dto;

import java.time.LocalDateTime;

public record GroupResponse(
    String id,
    String name,
    String description,
    LocalDateTime createdAt
) {}

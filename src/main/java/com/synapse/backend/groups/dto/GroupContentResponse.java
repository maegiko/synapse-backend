package com.synapse.backend.groups.dto;

import java.time.LocalDateTime;

public record GroupContentResponse(
    String id,
    String title,
    LocalDateTime createdAt
) {}

package com.synapse.backend.groups.dto;

import java.util.List;

public record GroupListResponse(
    List<GroupListItemResponse> groups,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {}

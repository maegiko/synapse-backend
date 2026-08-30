package com.synapse.backend.groups.dto;

import java.util.List;

public record GroupListResponse(
    List<GroupListItemResponse> groups
) {}

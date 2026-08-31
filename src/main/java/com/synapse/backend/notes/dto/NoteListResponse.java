package com.synapse.backend.notes.dto;

import java.util.List;

public record NoteListResponse(
    List<NoteSummaryResponse> notes,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {}

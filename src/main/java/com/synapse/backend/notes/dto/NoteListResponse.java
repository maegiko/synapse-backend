package com.synapse.backend.notes.dto;

import java.util.List;

public record NoteListResponse(
    List<NoteSummaryResponse> notes
) {}

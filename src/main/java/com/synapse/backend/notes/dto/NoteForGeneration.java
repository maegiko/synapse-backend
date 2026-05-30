package com.synapse.backend.notes.dto;

public record NoteForGeneration(
    Long id,
    NoteSummaryResponse summary
) {}

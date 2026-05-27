package com.synapse.backend.notes.dto;

import java.util.List;
import java.util.UUID;

public record NoteSummaryResponse(
    UUID id,
    String title,
    String overview,
    List<String> keypoints,
    List<ConceptSummary> concepts,
    List<String> importantTerms
) {}

package com.synapse.backend.notes.dto;

import java.util.List;

public record NoteSummaryResponse(
    String title,
    String overview,
    List<String> keypoints,
    List<ConceptSummary> concepts,
    List<String> importantTerms
) {}

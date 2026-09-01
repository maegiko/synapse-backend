package com.synapse.backend.notes.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

public record NoteSummaryResponse(
    String id,
    String title,
    String overview,
    List<String> keypoints,
    List<ConceptSummary> concepts,
    List<String> importantTerms,
    String groupId,
    // The LLM summary JSON never carries a pin state; a parsed summary is always unpinned
    // until the persistence layer supplies the saved value.
    @JsonSetter(nulls = Nulls.AS_EMPTY) boolean pinned
) {}

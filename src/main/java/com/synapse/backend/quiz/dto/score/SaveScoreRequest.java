package com.synapse.backend.quiz.dto.score;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SaveScoreRequest(
    @NotNull
    @Min(0)
    Integer score
) {}

package com.synapse.backend.quiz.dto.difficulty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateDifficultyRequest(
    @NotNull
    @Min(1)
    @Max(5)
    Integer difficulty
) {}

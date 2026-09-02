package com.synapse.backend.quiz.dto.difficulty;

import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The difficulty a user rates a quiz at.
 */
public record UpdateDifficultyRequest(
    @NotNull
    @Min(ValidationLimits.DIFFICULTY_MIN)
    @Max(ValidationLimits.DIFFICULTY_MAX)
    Integer difficulty
) {}

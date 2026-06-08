package com.synapse.backend.quiz.dto.create;

import java.util.List;

import com.synapse.backend.quiz.enums.QuestionType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateQuestionRequest(
    @NotBlank
    @Size(max = 1000)
    String question,

    @NotNull
    QuestionType questionType,

    @NotNull
    List<@Valid @NotNull CreateQuestionAnswer> answers
) {}

package com.synapse.backend.analytics.dto;

import java.time.LocalDate;

public record QuizDayResponse(
    LocalDate date,
    long attempts
) {}

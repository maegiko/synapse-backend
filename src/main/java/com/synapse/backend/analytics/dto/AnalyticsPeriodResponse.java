package com.synapse.backend.analytics.dto;

import java.time.LocalDate;

/** The window the figures cover: {@code days} calendar days ending today, both ends included. */
public record AnalyticsPeriodResponse(
    int days,
    LocalDate from,
    LocalDate to
) {}

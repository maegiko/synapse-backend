package com.synapse.backend.streak.dto;

import java.time.LocalDate;

public record StreakResponse(
    int currentStreak,
    int longestStreak,
    boolean activeToday,
    LocalDate lastActiveDate
) {}

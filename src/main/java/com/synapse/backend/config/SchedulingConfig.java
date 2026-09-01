package com.synapse.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the scheduled cleanup of abandoned registrations and expired verification tokens. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

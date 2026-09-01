package com.synapse.backend.email.dto;

import java.util.List;

/** The Resend send-email request body. */
public record ResendEmailRequest(
    String from,
    List<String> to,
    String subject,
    String text,
    String html
) {}

package com.synapse.backend.email.dto;

/** The Resend send-email response envelope, which carries the accepted message id. */
public record ResendEmailResponse(
    String id
) {}

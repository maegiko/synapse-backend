package com.synapse.backend.email.dto;

/**
 * One transactional email.
 *
 * @param to the recipient address.
 * @param subject the subject line.
 * @param text the plain text body.
 * @param html the HTML body.
 * @param idempotencyKey a stable non-secret key for this send, so a retry cannot duplicate the email.
 */
public record EmailMessage(
    String to,
    String subject,
    String text,
    String html,
    String idempotencyKey
) {}

package com.synapse.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.synapse.backend.auth.enums.EmailVerificationPurpose;

/**
 * The outcome of confirming a verification link.
 *
 * <p>{@code kind} tells the client which link was confirmed, so it never has to
 * guess from whether the visitor happens to be signed in already. A registration
 * link also signs the account in and carries its name and an access token; an
 * email-change link issues nothing and carries only the address the account now
 * uses, so the two session fields are left out of its JSON entirely.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerifyEmailResponse(
    EmailVerificationPurpose kind,
    String fullName,
    String email,
    String accessToken
) {}

package com.synapse.backend.auth.dto;

import com.synapse.backend.shared.validation.RequestText;
import com.synapse.backend.shared.validation.ValidationLimits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The ID token Google Identity Services returned, plus the time zone a new account would be
 * created in.
 *
 * <p>The credential is a signed JWT that only the backend is allowed to believe, so it is
 * bounded but not parsed here. The time zone is optional and follows registration: a client
 * that sends none, or cannot detect one, gets UTC. It is ignored for a returning user, whose
 * saved zone is theirs to change on the profile.</p>
 */
public record GoogleLoginRequest(
    @NotBlank
    @Size(max = ValidationLimits.GOOGLE_CREDENTIAL_MAX)
    String credential,

    @Size(max = ValidationLimits.TIME_ZONE_MAX)
    String timeZone
) {

    public GoogleLoginRequest {
        credential = RequestText.trimmed(credential);
        timeZone = RequestText.trimmed(timeZone);
    }

    /** A sign-in from a client that sends no time zone, which falls back to UTC. */
    public GoogleLoginRequest(String credential) {
        this(credential, null);
    }

}

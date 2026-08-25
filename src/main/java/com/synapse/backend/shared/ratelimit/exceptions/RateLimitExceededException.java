package com.synapse.backend.shared.ratelimit.exceptions;

import com.synapse.backend.shared.exceptions.TooManyRequestsException;

public class RateLimitExceededException extends TooManyRequestsException {

    public RateLimitExceededException(long retryAfterSeconds) {
        super(String.format("Too many requests. Try again in %d seconds.", retryAfterSeconds), retryAfterSeconds);
    }

}

package com.synapse.backend.analytics.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class InvalidAnalyticsPeriodException extends BadRequestException {

    public InvalidAnalyticsPeriodException(String message) {
        super(message);
    }

}

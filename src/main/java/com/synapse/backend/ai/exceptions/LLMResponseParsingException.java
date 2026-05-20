package com.synapse.backend.ai.exceptions;

import com.synapse.backend.shared.exceptions.BadGatewayException;

public class LLMResponseParsingException extends BadGatewayException {

    public LLMResponseParsingException(String message) {
        super(message);
    }

}

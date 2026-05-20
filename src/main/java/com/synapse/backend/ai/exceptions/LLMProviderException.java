package com.synapse.backend.ai.exceptions;

import com.synapse.backend.shared.exceptions.BadGatewayException;

public class LLMProviderException extends BadGatewayException {

    public LLMProviderException(String message) {
        super(message);
    }

}

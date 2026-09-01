package com.synapse.backend.email.exceptions;

import com.synapse.backend.shared.exceptions.BadGatewayException;

public class EmailProviderException extends BadGatewayException {

    public EmailProviderException(String message) {
        super(message);
    }

}

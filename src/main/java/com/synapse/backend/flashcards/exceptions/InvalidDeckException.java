package com.synapse.backend.flashcards.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class InvalidDeckException extends BadRequestException {

    public InvalidDeckException(String message) {
        super(message);
    }

}

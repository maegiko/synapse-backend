package com.synapse.backend.flashcards.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class EmptyDeckException extends BadRequestException {

    public EmptyDeckException(String message) {
        super(message);
    }

}

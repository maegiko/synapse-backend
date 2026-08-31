package com.synapse.backend.flashcards.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class InvalidFlashcardException extends BadRequestException {

    public InvalidFlashcardException(String message) {
        super(message);
    }

}

package com.synapse.backend.flashcards.exceptions;

import com.synapse.backend.shared.exceptions.NotFoundException;

public class FlashcardNotFound extends NotFoundException {

    public FlashcardNotFound(String message) {
        super(message);
    }

}

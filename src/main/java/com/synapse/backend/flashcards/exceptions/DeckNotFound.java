package com.synapse.backend.flashcards.exceptions;

import com.synapse.backend.shared.exceptions.NotFoundException;

public class DeckNotFound extends NotFoundException{

    public DeckNotFound(String message) {
        super(message);
    }

}

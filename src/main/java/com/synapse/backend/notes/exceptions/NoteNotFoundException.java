package com.synapse.backend.notes.exceptions;

import com.synapse.backend.shared.exceptions.NotFoundException;

public class NoteNotFoundException extends NotFoundException {

    public NoteNotFoundException(String message) {
        super(message);
    }

}

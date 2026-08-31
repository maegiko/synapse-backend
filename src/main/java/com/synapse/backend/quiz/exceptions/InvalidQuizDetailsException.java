package com.synapse.backend.quiz.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class InvalidQuizDetailsException extends BadRequestException {

    public InvalidQuizDetailsException(String message) {
        super(message);
    }

}

package com.synapse.backend.quiz.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class InvalidQuizScoreException extends BadRequestException {

    public InvalidQuizScoreException(String message) {
        super(message);
    }

}

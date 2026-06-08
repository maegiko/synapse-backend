package com.synapse.backend.quiz.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class CreateQuestionInputException extends BadRequestException {

    public CreateQuestionInputException(String message) {
        super(message);
    }

}

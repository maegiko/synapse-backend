package com.synapse.backend.quiz.exceptions;

import com.synapse.backend.shared.exceptions.BadRequestException;

public class QuizNotFound extends BadRequestException {

    public QuizNotFound(String message) {
        super(message);
    }

}

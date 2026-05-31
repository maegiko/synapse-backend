package com.synapse.backend.quiz.exceptions;

import com.synapse.backend.shared.exceptions.NotFoundException;

public class QuizNotFound extends NotFoundException {

    public QuizNotFound(String message) {
        super(message);
    }

}

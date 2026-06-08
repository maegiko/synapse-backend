package com.synapse.backend.quiz.exceptions;

import com.synapse.backend.shared.exceptions.NotFoundException;

public class QuestionNotFound extends NotFoundException {

    public QuestionNotFound(String message) {
        super(message);
    }

}

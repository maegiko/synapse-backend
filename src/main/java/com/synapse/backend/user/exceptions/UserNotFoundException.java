package com.synapse.backend.user.exceptions;

import com.synapse.backend.shared.exceptions.NotFoundException;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(Long id) {
        super(String.format("The user with Id %d does not exist.", id));
    }

}

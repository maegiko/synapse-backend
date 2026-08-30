package com.synapse.backend.groups.exceptions;

import com.synapse.backend.shared.exceptions.NotFoundException;

public class GroupNotFound extends NotFoundException {

    public GroupNotFound(String message) {
        super(message);
    }

}

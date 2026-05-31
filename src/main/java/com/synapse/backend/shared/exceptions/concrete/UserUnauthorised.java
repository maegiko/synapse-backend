package com.synapse.backend.shared.exceptions.concrete;

import com.synapse.backend.shared.exceptions.UnauthorisedException;

public class UserUnauthorised extends UnauthorisedException {

    public UserUnauthorised(String message) {
        super(message);
    }

}

package com.synapse.backend.user;

import org.springframework.stereotype.Service;

import com.synapse.backend.user.dto.MyDetailsResponse;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public MyDetailsResponse getMyDetails(Long userId) {
        return null;
    }
}

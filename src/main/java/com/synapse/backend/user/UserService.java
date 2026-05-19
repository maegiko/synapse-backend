package com.synapse.backend.user;

import org.springframework.stereotype.Service;

import com.synapse.backend.user.dto.UserDetailsResponse;
import com.synapse.backend.user.exceptions.UserNotFoundException;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Gets the user's details by Id.
     * @param userId the user's Id.
     * @return the user's full name and email.
     * @throws UserNotFoundException if the user Id does not exist in DB.
     */
    public UserDetailsResponse getUserDetails(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        return new UserDetailsResponse(
            user.getName(),
            user.getEmail()
        );
    }

}

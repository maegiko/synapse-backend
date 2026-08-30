package com.synapse.backend.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.synapse.backend.auth.exceptions.EmailAlreadyExistsException;
import com.synapse.backend.user.dto.UpdateUserDetailsRequest;
import com.synapse.backend.user.dto.UserDetailsResponse;
import com.synapse.backend.user.exceptions.InvalidUserDetailsException;
import com.synapse.backend.user.exceptions.UserNotFoundException;

import jakarta.transaction.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Gets the user's details by ID.
     *
     * @param userId the user's ID.
     * @return the user's full name, email, and lifetime flashcards reviewed.
     * @throws UserNotFoundException if the user ID does not exist in DB.
     */
    public UserDetailsResponse getUserDetails(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        return new UserDetailsResponse(
            user.getName(),
            user.getEmail(),
            user.getTotalFlashcardsReviewed()
        );
    }

    /**
     * Updates the full name and/or email of the user.
     *
     * <p>Only the supplied fields are changed. The request arrives with its full
     * name trimmed and its email trimmed and lowercased, matching registration.
     * Submitting the email the user already has is allowed.</p>
     *
     * @param userId the user's ID.
     * @param req the validated details to update, with at least one field supplied.
     * @return the user's updated full name, email, and lifetime flashcards reviewed.
     * @throws InvalidUserDetailsException if no field is supplied or the supplied email is blank.
     * @throws UserNotFoundException if the user ID does not exist in DB.
     * @throws EmailAlreadyExistsException if the email belongs to a different user, including when a
     *     concurrent request claims it first and the unique constraint rejects the write.
     */
    @Transactional
    public UserDetailsResponse updateUserDetails(Long userId, UpdateUserDetailsRequest req) {
        String fullName = req.fullName();
        String email = req.email();

        if (fullName == null && email == null)
            throw new InvalidUserDetailsException("At least one of fullName or email must be supplied.");

        if (email != null && email.isBlank())
            throw new InvalidUserDetailsException("email: must not be blank");

        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        if (fullName != null)
            user.updateFullName(fullName);

        if (email != null) {
            if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email))
                throw new EmailAlreadyExistsException(email);

            user.updateEmail(email);
        }

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyExistsException(user.getEmail());
        }

        return new UserDetailsResponse(
            user.getName(),
            user.getEmail(),
            user.getTotalFlashcardsReviewed()
        );
    }

}

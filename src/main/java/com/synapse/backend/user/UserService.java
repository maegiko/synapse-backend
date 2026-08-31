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
    private final UserTimeZoneService userTimeZoneService;

    public UserService(UserRepository userRepository, UserTimeZoneService userTimeZoneService) {
        this.userRepository = userRepository;
        this.userTimeZoneService = userTimeZoneService;
    }

    /**
     * Gets the user's details by ID.
     *
     * @param userId the user's ID.
     * @return the user's full name, email, lifetime flashcards reviewed, and time zone.
     * @throws UserNotFoundException if the user ID does not exist in DB.
     */
    public UserDetailsResponse getUserDetails(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        return toUserDetailsResponse(user);
    }

    /**
     * Updates the full name, email, and/or time zone of the user.
     *
     * <p>Only the supplied fields are changed. The request arrives with its full
     * name and time zone trimmed and its email trimmed and lowercased, matching
     * registration. Submitting the email the user already has is allowed.</p>
     *
     * <p>A new time zone moves every later calendar-day boundary — streak days, deck
     * due dates — from the next request on. Already recorded streak days and stored
     * timestamps are left exactly as they are, so travelling does not rewrite history.</p>
     *
     * @param userId the user's ID.
     * @param req the validated details to update, with at least one field supplied.
     * @return the user's updated full name, email, lifetime flashcards reviewed, and time zone.
     * @throws InvalidUserDetailsException if no field is supplied, the supplied email is blank, or
     *     the supplied time zone is not a real IANA zone.
     * @throws UserNotFoundException if the user ID does not exist in DB.
     * @throws EmailAlreadyExistsException if the email belongs to a different user, including when a
     *     concurrent request claims it first and the unique constraint rejects the write.
     */
    @Transactional
    public UserDetailsResponse updateUserDetails(Long userId, UpdateUserDetailsRequest req) {
        String fullName = req.fullName();
        String email = req.email();
        String timeZone = req.timeZone();

        if (fullName == null && email == null && timeZone == null)
            throw new InvalidUserDetailsException(
                "At least one of fullName, email, or timeZone must be supplied."
            );

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

        if (timeZone != null)
            user.updateTimeZone(userTimeZoneService.validated(timeZone));

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyExistsException(user.getEmail());
        }

        return toUserDetailsResponse(user);
    }

    private UserDetailsResponse toUserDetailsResponse(User user) {
        return new UserDetailsResponse(
            user.getName(),
            user.getEmail(),
            user.getTotalFlashcardsReviewed(),
            user.getTimeZone()
        );
    }

}

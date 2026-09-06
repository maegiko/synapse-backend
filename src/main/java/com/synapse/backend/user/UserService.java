package com.synapse.backend.user;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.synapse.backend.auth.EmailVerificationService;
import com.synapse.backend.auth.exceptions.EmailAlreadyExistsException;
import com.synapse.backend.user.dto.ChangeEmailRequest;
import com.synapse.backend.user.dto.EmailChangeResponse;
import com.synapse.backend.user.dto.UpdateUserDetailsRequest;
import com.synapse.backend.user.dto.UserDetailsResponse;
import com.synapse.backend.user.exceptions.InvalidUserDetailsException;
import com.synapse.backend.user.exceptions.UserNotFoundException;

import jakarta.transaction.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserTimeZoneService userTimeZoneService;
    private final UserNameService userNameService;
    private final EmailVerificationService emailVerificationService;

    public UserService(
        UserRepository userRepository,
        UserTimeZoneService userTimeZoneService,
        UserNameService userNameService,
        EmailVerificationService emailVerificationService
    ) {
        this.userRepository = userRepository;
        this.userTimeZoneService = userTimeZoneService;
        this.userNameService = userNameService;
        this.emailVerificationService = emailVerificationService;
    }

    /**
     * Gets the user's details by ID.
     *
     * @param userId the user's ID.
     * @return the user's full name, email, lifetime flashcards reviewed, time zone, and which ways in the
     *     account has.
     * @throws UserNotFoundException if the user ID does not exist in DB.
     */
    public UserDetailsResponse getUserDetails(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        return toUserDetailsResponse(user);
    }

    /**
     * Updates the full name and/or time zone of the user.
     *
     * <p>Only the supplied fields are changed. The request arrives with its full
     * name and time zone trimmed, and the name is capitalised exactly as registration
     * capitalises it, so a name reads the same however it was set. The email address
     * cannot be changed here: it only moves once the new address has been confirmed,
     * which {@link #requestEmailChange} starts.</p>
     *
     * <p>A new time zone moves every later calendar-day boundary — streak days, deck
     * due dates — from the next request on. Already recorded streak days and stored
     * timestamps are left exactly as they are, so travelling does not rewrite history.</p>
     *
     * @param userId the user's ID.
     * @param req the validated details to update, with at least one field supplied.
     * @return the user's updated full name, email, lifetime flashcards reviewed, and time zone.
     * @throws InvalidUserDetailsException if no field is supplied, or the supplied time zone is not a
     *     real IANA zone.
     * @throws UserNotFoundException if the user ID does not exist in DB.
     */
    @Transactional
    public UserDetailsResponse updateUserDetails(Long userId, UpdateUserDetailsRequest req) {
        String fullName = req.fullName();
        String timeZone = req.timeZone();

        if (fullName == null && timeZone == null)
            throw new InvalidUserDetailsException("At least one of fullName or timeZone must be supplied.");

        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        if (fullName != null)
            user.updateFullName(userNameService.capitalised(fullName));

        if (timeZone != null)
            user.updateTimeZone(userTimeZoneService.validated(timeZone));

        userRepository.save(user);

        return toUserDetailsResponse(user);
    }

    /**
     * Starts a confirmed change of the user's email address.
     *
     * <p>Nothing about the account changes yet. A single-use link is emailed to the
     * proposed address and the account keeps its current address, and keeps logging
     * in with it, until that link is confirmed. Asking again replaces the pending
     * request, and an abandoned request simply expires.</p>
     *
     * @param userId the user's ID.
     * @param req the validated proposed address, already trimmed and lowercased.
     * @return the pending address and when it expires, or null if it is the address the user already has.
     * @throws UserNotFoundException if the user ID does not exist in DB.
     * @throws EmailAlreadyExistsException if the address already belongs to another account.
     * @throws EmailProviderException if the confirmation email could not be sent.
     */
    public EmailChangeResponse requestEmailChange(Long userId, ChangeEmailRequest req) {
        String email = req.email();
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        if (email.equals(user.getEmail()))
            return null;

        if (userRepository.existsByEmail(email))
            throw new EmailAlreadyExistsException(email);

        LocalDateTime expiresAt = emailVerificationService.sendEmailChangeVerification(user, email);

        return new EmailChangeResponse(email, expiresAt);
    }

    private UserDetailsResponse toUserDetailsResponse(User user) {
        return new UserDetailsResponse(
            user.getName(),
            user.getEmail(),
            user.getTotalFlashcardsReviewed(),
            user.getTimeZone(),
            user.hasPassword(),
            user.getGoogleSubject() != null
        );
    }

}

package com.synapse.backend.user;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;

import com.synapse.backend.user.exceptions.InvalidUserDetailsException;
import com.synapse.backend.user.exceptions.UserNotFoundException;

/**
 * The one place a user's time zone is validated and turned into a calendar date.
 *
 * <p>Event timestamps stay UTC everywhere. What a user's time zone decides is which
 * calendar day an instant falls on: whether a streak day is today, and the date a
 * reviewed deck becomes due. Any future date-boundary rule should ask this service
 * for the day rather than calling {@code LocalDate.now(clock)} again, so every
 * boundary in the app moves at the same local midnight.</p>
 */
@Service
public class UserTimeZoneService {

    /** What a user gets when registration supplies no time zone. */
    public static final String DEFAULT_TIME_ZONE = "UTC";

    private final UserRepository userRepository;
    private final Clock clock;

    public UserTimeZoneService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * Validates an optional time zone, falling back to UTC when none was supplied.
     *
     * @param timeZone a trimmed IANA identifier, or null/blank when the client sent none.
     * @return the supplied zone's identifier, or {@code UTC}.
     * @throws InvalidUserDetailsException if a value was supplied but is not a real zone.
     */
    public String resolveOrDefault(String timeZone) {
        if (timeZone == null || timeZone.isBlank())
            return DEFAULT_TIME_ZONE;

        return validated(timeZone);
    }

    /**
     * Validates a time zone the client chose to supply.
     *
     * @param timeZone a trimmed IANA identifier.
     * @return the zone's canonical identifier.
     * @throws InvalidUserDetailsException if the value is blank or is not a real zone.
     */
    public String validated(String timeZone) {
        try {
            return ZoneId.of(timeZone.trim()).getId();
        } catch (DateTimeException ex) {
            throw new InvalidUserDetailsException("timeZone: must be a valid IANA time zone");
        }
    }

    /**
     * Returns the calendar date it currently is for a user.
     *
     * <p>Taken from the injected UTC clock and the user's saved zone, so tests pin the
     * instant and daylight saving is handled by the zone's own rules. Changing the saved
     * zone moves this boundary from the next request on; it never rewrites history.</p>
     *
     * @param userId the id of the authenticated user.
     * @return today's date where the user is.
     * @throws UserNotFoundException if the user ID does not exist in DB.
     */
    public LocalDate today(Long userId) {
        return LocalDate.ofInstant(clock.instant(), zoneOf(userId));
    }

    /**
     * Returns a user's saved time zone.
     *
     * @param userId the id of the authenticated user.
     * @return the user's zone.
     * @throws UserNotFoundException if the user ID does not exist in DB.
     */
    public ZoneId zoneOf(Long userId) {
        String timeZone = userRepository
            .findTimeZoneById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        return ZoneId.of(timeZone);
    }

}

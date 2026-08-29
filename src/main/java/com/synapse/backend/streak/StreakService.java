package com.synapse.backend.streak;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.synapse.backend.streak.dto.StreakResponse;

@Service
public class StreakService {
    private final StreakPersistenceService persistenceService;
    private final Clock clock;

    public StreakService(StreakPersistenceService persistenceService, Clock clock) {
        this.persistenceService = persistenceService;
        this.clock = clock;
    }

    /**
     * Records study activity for the current UTC day.
     *
     * <p>Call this only after a qualifying workflow has persisted its own data, so a failed
     * generation, save, or ownership check never awards a streak day. Recording the same day
     * again is a no-op.</p>
     *
     * @param userId the id of the authenticated user.
     */
    public void recordActivity(Long userId) {
        persistenceService.recordActivityDay(userId, LocalDate.now(clock));
    }

    /**
     * Returns the study streak of a user, measured in UTC calendar days.
     *
     * <p>The current streak is the run of consecutive days ending on the most recent activity
     * date, and counts only while that date is today or yesterday. The longest streak is the
     * longest run in the user's whole history.</p>
     *
     * @param userId the id of the authenticated user.
     * @return the current streak, longest streak, whether today is active, and the last active date.
     */
    public StreakResponse getStreak(Long userId) {
        List<LocalDate> activityDates = persistenceService.getActivityDates(userId);

        if (activityDates.isEmpty())
            return new StreakResponse(0, 0, false, null);

        LocalDate today = LocalDate.now(clock);
        LocalDate lastActiveDate = activityDates.get(0);
        boolean streakIsAlive = lastActiveDate.equals(today) || lastActiveDate.equals(today.minusDays(1));

        return new StreakResponse(
            streakIsAlive ? countRunFromMostRecent(activityDates) : 0,
            countLongestRun(activityDates),
            lastActiveDate.equals(today),
            lastActiveDate
        );
    }

    /**
     * Counts consecutive days back from the most recent activity date.
     *
     * @param activityDates the user's activity dates ordered from newest to oldest.
     * @return the length of the run containing the most recent activity date.
     */
    private int countRunFromMostRecent(List<LocalDate> activityDates) {
        int run = 1;

        for (int i = 1; i < activityDates.size(); i++) {
            if (!activityDates.get(i).plusDays(1).equals(activityDates.get(i - 1)))
                break;

            run++;
        }

        return run;
    }

    /**
     * Counts the longest run of consecutive days in the user's history.
     *
     * @param activityDates the user's activity dates ordered from newest to oldest.
     * @return the length of the longest run of consecutive activity dates.
     */
    private int countLongestRun(List<LocalDate> activityDates) {
        int longestRun = 1;
        int run = 1;

        for (int i = 1; i < activityDates.size(); i++) {
            run = activityDates.get(i).plusDays(1).equals(activityDates.get(i - 1)) ? run + 1 : 1;
            longestRun = Math.max(longestRun, run);
        }

        return longestRun;
    }

}

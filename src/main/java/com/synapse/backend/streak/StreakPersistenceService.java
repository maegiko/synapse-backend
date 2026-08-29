package com.synapse.backend.streak;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.synapse.backend.streak.entities.StreakActivity;
import com.synapse.backend.streak.repositories.StreakActivityRepository;

import jakarta.transaction.Transactional;

@Service
public class StreakPersistenceService {
    private final StreakActivityRepository streakActivityRepository;

    public StreakPersistenceService(StreakActivityRepository streakActivityRepository) {
        this.streakActivityRepository = streakActivityRepository;
    }

    /**
     * Records one activity day for a user.
     *
     * <p>The insert is an atomic {@code ON CONFLICT DO NOTHING}, so repeated and concurrent
     * calls for the same user and date leave a single row instead of racing on a read.</p>
     *
     * @param userId the id of the authenticated user.
     * @param activityDate the UTC date the qualifying activity happened on.
     */
    @Transactional
    public void recordActivityDay(Long userId, LocalDate activityDate) {
        streakActivityRepository.insertActivityDayIfAbsent(userId, activityDate);
    }

    /**
     * Returns every activity date recorded for a user.
     *
     * @param userId the id of the authenticated user.
     * @return the user's activity dates ordered from newest to oldest.
     */
    public List<LocalDate> getActivityDates(Long userId) {
        return streakActivityRepository
            .findByUserIdOrderByActivityDateDesc(userId)
            .stream()
            .map(StreakActivity::getActivityDate)
            .toList();
    }

}

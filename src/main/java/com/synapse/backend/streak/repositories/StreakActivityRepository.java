package com.synapse.backend.streak.repositories;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.streak.entities.StreakActivity;

public interface StreakActivityRepository extends JpaRepository<StreakActivity, Long> {

    List<StreakActivity> findByUserIdOrderByActivityDateDesc(Long userId);

    @Modifying
    @Query(value = """
        INSERT INTO streak_activity (user_id, activity_date)
        VALUES (:userId, :activityDate)
        ON CONFLICT (user_id, activity_date) DO NOTHING
    """, nativeQuery = true)
    long insertActivityDayIfAbsent(@Param("userId") Long userId, @Param("activityDate") LocalDate activityDate);

}

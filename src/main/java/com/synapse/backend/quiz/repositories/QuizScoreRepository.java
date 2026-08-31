package com.synapse.backend.quiz.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.analytics.dto.QuizAttemptResponse;
import com.synapse.backend.quiz.entities.QuizScore;

public interface QuizScoreRepository extends JpaRepository<QuizScore, Long> {

    Optional<QuizScore> findByPublicIdAndQuizId(String publicId, Long quizId);

    List<QuizScore> findByQuizIdOrderByCreatedAtDesc(Long quizId);

    /**
     * One row per local day the user attempted a quiz on, summed in the database rather than
     * by loading the history. Attempt instants are stored as UTC, so they are converted to
     * the user's zone before the calendar date is taken.
     *
     * @param userId the id of the authenticated user.
     * @param zone the user's IANA time zone, which decides where each day starts.
     * @param fromUtc the inclusive UTC start of the window.
     * @param toUtc the exclusive UTC end of the window.
     * @return the user's daily totals, oldest day first.
     */
    @Query(value = """
        SELECT (s.created_at AT TIME ZONE 'UTC' AT TIME ZONE CAST(:zone AS text))::date AS "day",
               COUNT(*) AS "attempts",
               COALESCE(SUM(s.duration_seconds), 0) AS "studySeconds"
        FROM quiz_score s
        WHERE s.user_id = :userId
          AND s.created_at >= :fromUtc
          AND s.created_at < :toUtc
        GROUP BY "day"
        ORDER BY "day"
    """, nativeQuery = true)
    List<DailyQuizAttemptProjection> findDailyTotals(
        @Param("userId") Long userId,
        @Param("zone") String zone,
        @Param("fromUtc") LocalDateTime fromUtc,
        @Param("toUtc") LocalDateTime toUtc
    );

    /**
     * The window's whole-period quiz totals.
     *
     * <p>A percentage is only defined when the attempt snapshotted at least one question, so
     * an attempt on an empty quiz is excluded from the averages by {@code NULLIF} rather than
     * dividing by zero. It still counts towards {@code attempts}. Averaging
     * {@code duration_seconds} skips the attempts that reported none, so a history of rows
     * saved before durations existed does not drag the average down.</p>
     *
     * @param userId the id of the authenticated user.
     * @param fromUtc the inclusive UTC start of the window.
     * @param toUtc the exclusive UTC end of the window.
     * @return one row, with null averages when the window holds no attempts.
     */
    @Query(value = """
        SELECT COUNT(*) AS "attempts",
               COUNT(DISTINCT s.quiz_id) AS "distinctQuizzes",
               AVG(s.score * 100.0 / NULLIF(s.total_questions, 0)) AS "averagePercentage",
               MAX(s.score * 100.0 / NULLIF(s.total_questions, 0)) AS "bestPercentage",
               AVG(s.duration_seconds) AS "averageDurationSeconds"
        FROM quiz_score s
        WHERE s.user_id = :userId
          AND s.created_at >= :fromUtc
          AND s.created_at < :toUtc
    """, nativeQuery = true)
    QuizAttemptTotalsProjection findTotals(
        @Param("userId") Long userId,
        @Param("fromUtc") LocalDateTime fromUtc,
        @Param("toUtc") LocalDateTime toUtc
    );

    /**
     * Every attempt in the window, oldest first, with the quiz it belongs to.
     *
     * <p>Unlike the figures above this is the history itself, because the endpoint returns it.
     * The improvement trend is derived from these rows rather than queried again.</p>
     *
     * @param userId the id of the authenticated user.
     * @param fromUtc the inclusive UTC start of the window.
     * @param toUtc the exclusive UTC end of the window.
     * @return the attempts in chronological order.
     */
    @Query("""
        SELECT new com.synapse.backend.analytics.dto.QuizAttemptResponse(
            s.publicId, q.publicId, q.title, s.score, s.totalQuestions, s.durationSeconds, s.createdAt
        )
        FROM QuizScore s, Quiz q
        WHERE q.id = s.quizId
          AND s.userId = :userId
          AND s.createdAt >= :fromUtc
          AND s.createdAt < :toUtc
        ORDER BY s.createdAt ASC, s.id ASC
    """)
    List<QuizAttemptResponse> findAttempts(
        @Param("userId") Long userId,
        @Param("fromUtc") LocalDateTime fromUtc,
        @Param("toUtc") LocalDateTime toUtc
    );

}
